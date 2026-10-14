package gd.invernadero.simulador.service;

import gd.invernadero.simulador.config.RabbitMQConfig;
import gd.invernadero.simulador.dto.ConfiguracionUmbralesDTO;
import gd.invernadero.simulador.dto.DatosInvernaderoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneradorTelemetriaService {

    private final RabbitTemplate rabbitTemplate;
    private final GestorActuadoresService gestorActuadores;
    private final GestorUmbralesService gestorUmbrales;
    private final Random random = new Random();

    @Value("${simulador.frecuencia-envio-ms:3000}")
    private long frecuenciaMs;

    /** Horas simuladas por hora real. 60 => 1 min real = 1 h simulada (día de 24 min). 1 => tiempo real. */
    @Value("${simulador.aceleracion:60}")
    private double aceleracion;

    @Value("${simulador.hora-inicial:8}")
    private double horaSimulada;

    // Estado físico persistente
    private double temperatura = 22, humedadSuelo = 65, humedadAire = 60;

    // Estado REAL de los actuadores (el que se publica)
    private boolean caloventor, humidificador, ventanales, bomba;
    private boolean persianasEnrolladas = true;   // enrolladas = recogidas = entra la luz

    // Última decisión AUTOMÁTICA (base de la histéresis, independiente del modo manual)
    private boolean autoCaloventor, autoHumidificador, autoVentanales, autoBomba;
    private boolean autoPersianas = true;

    @Scheduled(fixedDelayString = "${simulador.frecuencia-envio-ms:3000}")
    public void ejecutarCicloSimulacion() {
        ConfiguracionUmbralesDTO u = gestorUmbrales.getUmbrales();

        // 0. Reloj simulado propio: avanza por tick, no depende del reloj de pared
        horaSimulada = (horaSimulada + frecuenciaMs / 3_600_000.0 * aceleracion) % 24;
        double sol  = Math.max(0, Math.sin(Math.PI * (horaSimulada - 6) / 12));   // 0..1
        double fase = Math.sin(2 * Math.PI * (horaSimulada - 9) / 24);
        double tExt = 17 + 8 * fase;
        double hExt = 65 - 15 * fase;
        double luzExterior = 300 + 750 * sol;
        double luz = 300 + (luzExterior - 300) * (persianasEnrolladas ? 1.0 : 0.6) + ruido(8);
        boolean esDeDia = sol > 0.2;   // por encima de ~450 lux exteriores

        // 1. Física: usa el estado actual de los actuadores
        temperatura  += (tExt - temperatura) * (ventanales ? 0.08 : 0.02)
                + (caloventor ? 0.4 : 0) + sol * 0.15 + ruido(0.05);
        humedadAire  += (hExt - humedadAire) * (ventanales ? 0.08 : 0.01)
                + (humidificador ? 1.0 : 0) + (bomba ? 0.2 : 0)
                - (caloventor ? 0.15 : 0) + ruido(0.1);
        humedadSuelo += (bomba ? 2.0 : 0) - (0.05 + 0.05 * sol) + ruido(0.02);
        humedadAire  = Math.max(0, Math.min(100, humedadAire));
        humedadSuelo = Math.max(0, Math.min(100, humedadSuelo));

        // 2. Reglas automáticas con histéresis (sobre la decisión AUTO previa) e interbloqueo
        boolean cVent = alto(temperatura, u.getTempMax(), 1.0, autoVentanales)
                || (alto(humedadAire, u.getHumAireMax(), 3.0, autoVentanales)
                && temperatura > u.getTempMin() + 2.0);          // no ventilar si hace frío
        boolean cCal  = bajo(temperatura, u.getTempMin(), 1.0, autoCaloventor) && !cVent;
        boolean cHum  = bajo(humedadAire, u.getHumAireMin(), 3.0, autoHumidificador);
        boolean cPers = !alto(luzExterior, u.getLuzMax(), 50, !autoPersianas);  // baja si hay exceso de luz
        boolean cBomb = autoBomba ? humedadSuelo < u.getHumSueloMax()           // riega hasta el máximo
                : humedadSuelo < u.getHumSueloMin();
        autoCaloventor = cCal; autoHumidificador = cHum; autoVentanales = cVent;
        autoPersianas = cPers; autoBomba = cBomb;

        // 3. Resolución (el modo manual tiene prioridad)
        boolean pCal = caloventor, pHum = humidificador, pVen = ventanales, pPer = persianasEnrolladas, pBom = bomba;
        caloventor          = gestorActuadores.resolverEstado("caloventor", cCal);
        humidificador       = gestorActuadores.resolverEstado("humidificador", cHum);
        ventanales          = gestorActuadores.resolverEstado("ventanales", cVent);
        persianasEnrolladas = gestorActuadores.resolverEstado("persianas", cPers);
        bomba               = gestorActuadores.resolverEstado("bomba", cBomb);
        cambio("caloventor", pCal, caloventor);
        cambio("humidificador", pHum, humidificador);
        cambio("ventanales", pVen, ventanales);
        cambio("persianas", pPer, persianasEnrolladas);
        cambio("bomba", pBom, bomba);

        DatosInvernaderoDTO telemetria = new DatosInvernaderoDTO(
                redondear(temperatura), redondear(humedadSuelo), redondear(humedadAire),
                (int) Math.max(0, Math.round(luz)),
                caloventor, humidificador, ventanales, persianasEnrolladas, bomba,
                esDeDia);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.INTERCAMBIO_INVERNADERO, RabbitMQConfig.RUTA_TELEMETRIA, telemetria);
        } catch (AmqpException e) {
            log.warn("Broker no disponible; se descarta la muestra: {}", e.getMessage());
            return;
        }
        log.debug("Telemetría [{}h] T={}°C HS={}% HA={}% luz={}", String.format("%.1f", horaSimulada),
                telemetria.getTemperatura(), telemetria.getHumedadSuelo(),
                telemetria.getHumedadAire(), telemetria.getLuminosidad());
    }

    private boolean bajo(double v, double umbral, double banda, boolean previo) {
        return previo ? v < umbral + banda : v < umbral;
    }
    private boolean alto(double v, double umbral, double banda, boolean previo) {
        return previo ? v > umbral - banda : v > umbral;
    }
    private void cambio(String nombre, boolean antes, boolean ahora) {
        if (antes != ahora) log.info("Actuador {} -> {}", nombre, ahora ? "ON/ACTIVO" : "OFF/INACTIVO");
    }
    private double ruido(double s) { return random.nextGaussian() * s; }
    private double redondear(double v) { return Math.round(v * 100.0) / 100.0; }
}