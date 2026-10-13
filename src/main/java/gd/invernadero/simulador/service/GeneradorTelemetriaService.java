package gd.invernadero.simulador.service;

import gd.invernadero.simulador.config.RabbitMQConfig;
import gd.invernadero.simulador.dto.ConfiguracionUmbralesDTO;
import gd.invernadero.simulador.dto.DatosInvernaderoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Value("${simulador.aceleracion:60}")   // 1 min real = 1 h simulada
    private double aceleracion;

    // Estado físico persistente
    private double temperatura = 22, humedadSuelo = 65, humedadAire = 60;
    // Último estado real de los actuadores
    private boolean caloventor, humidificador, ventanales, persianas = true, bomba;

    @Scheduled(fixedRateString = "${simulador.frecuencia-envio-ms:3000}")
    public void ejecutarCicloSimulacion() {
        ConfiguracionUmbralesDTO u = gestorUmbrales.getUmbrales();

        double hora = (System.currentTimeMillis() / 3_600_000.0 * aceleracion * 60) % 24;
        double sol = Math.max(0, Math.sin(Math.PI * (hora - 6) / 12));            // 0..1
        double tExt = 17 + 8 * Math.sin(2 * Math.PI * (hora - 9) / 24);
        double hExt = 65 - 15 * Math.sin(2 * Math.PI * (hora - 9) / 24);
        double luzExterior = 300 + 750 * sol;   // base 300 conserva el rango original del simulador
        double luz = 300 + (luzExterior - 300) * (persianas ? 1.0 : 0.6);

        // 1. Física: la física usa el estado actual de los actuadores
        temperatura += (tExt - temperatura) * (ventanales ? 0.08 : 0.02)
                + (caloventor ? 0.4 : 0) + sol * 0.15 + ruido(0.05);
        humedadAire += (hExt - humedadAire) * (ventanales ? 0.08 : 0.01)
                + (humidificador ? 1.0 : 0) + (bomba ? 0.2 : 0)
                - (caloventor ? 0.15 : 0) + ruido(0.1);
        humedadSuelo += (bomba ? 2.0 : 0) - (0.05 + 0.05 * sol) + ruido(0.02);
        humedadAire  = Math.max(0, Math.min(100, humedadAire));
        humedadSuelo = Math.max(0, Math.min(100, humedadSuelo));

        // 2. Reglas automáticas con histéresis e interbloqueos
        boolean cCalo = encenderSiBajo(temperatura, u.getTempMin(), 1.0, caloventor);
        boolean cHum  = encenderSiBajo(humedadAire, u.getHumAireMin(), 3.0, humidificador);
        boolean cVent = encenderSiAlto(temperatura, u.getTempMax(), 1.0, ventanales)
                || (encenderSiAlto(humedadAire, u.getHumAireMax(), 3.0, ventanales)
                && temperatura > u.getTempMin() + 2.0);          // no ventilar si hace frío
        boolean cPers = !encenderSiAlto(luzExterior, u.getLuzMax(), 50, !persianas);   // sombra si hay exceso de luz
        boolean cBomb = bomba ? humedadSuelo < u.getHumSueloMax()          // riega hasta el máximo
                : humedadSuelo < u.getHumSueloMin();

        // 3. Resolución (el modo manual tiene prioridad)
        caloventor    = gestorActuadores.resolverEstado("caloventor", cCalo);
        humidificador = gestorActuadores.resolverEstado("humidificador", cHum);
        ventanales    = gestorActuadores.resolverEstado("ventanales", cVent);
        persianas     = gestorActuadores.resolverEstado("persianas", cPers);
        bomba         = gestorActuadores.resolverEstado("bomba", cBomb);

        DatosInvernaderoDTO t = new DatosInvernaderoDTO(
                redondear(temperatura), redondear(humedadSuelo), redondear(humedadAire),
                (int) Math.round(luz + ruido(8)),
                caloventor, humidificador, ventanales, persianas, bomba);
        rabbitTemplate.convertAndSend(RabbitMQConfig.INTERCAMBIO_INVERNADERO, RabbitMQConfig.RUTA_TELEMETRIA, t);
    }

    private boolean encenderSiBajo(double v, double umbral, double banda, boolean previo) {
        return previo ? v < umbral + banda : v < umbral;
    }
    private boolean encenderSiAlto(double v, double umbral, double banda, boolean previo) {
        return previo ? v > umbral - banda : v > umbral;
    }
    private double ruido(double s) { return random.nextGaussian() * s; }
    private double redondear(double v) { return Math.round(v * 100.0) / 100.0; }
}