package com.invernadero.simulador.service;

import com.invernadero.simulador.config.RabbitMQConfig;
import com.invernadero.simulador.dto.ConfiguracionUmbralesDTO;
import com.invernadero.simulador.dto.DatosInvernaderoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    @Scheduled(fixedRateString = "${simulador.frecuencia-envio-ms:3000}")
    public void ejecutarCicloSimulacion() {
        ConfiguracionUmbralesDTO u = gestorUmbrales.getUmbrales();

        // 1. Simulación estocástica de variables
        Double temperatura = Math.round((16.0 + (random.nextDouble() * 15)) * 100.0) / 100.0;
        Double humedadSuelo = Math.round((35.0 + (random.nextDouble() * 50)) * 100.0) / 100.0;
        Double humedadAire = Math.round((40.0 + (random.nextDouble() * 50)) * 100.0) / 100.0;
        Integer luminosidad = 300 + random.nextInt(750);

        // 2. Disparadores autónomos comparados contra los umbrales numéricos dinámicos
        boolean condicionCaloventor = temperatura < u.getTempMin();
        boolean condicionHumidificador = humedadAire < u.getHumAireMin();
        boolean condicionVentanales = temperatura > u.getTempMax() || humedadAire > u.getHumAireMax();
        boolean condicionPersianas = luminosidad <= u.getLuzMax();
        boolean condicionBomba = humedadSuelo < u.getHumSueloMin();

        // 3. Resolución con prioridad manual si corresponde
        boolean caloventorActivo = gestorActuadores.resolverEstado("caloventor", condicionCaloventor);
        boolean humidificadorActivo = gestorActuadores.resolverEstado("humidificador", condicionHumidificador);
        boolean ventanalesAbiertos = gestorActuadores.resolverEstado("ventanales", condicionVentanales);
        boolean persianasEnrolladas = gestorActuadores.resolverEstado("persianas", condicionPersianas);
        boolean bombaActiva = gestorActuadores.resolverEstado("bomba", condicionBomba);

        DatosInvernaderoDTO telemetria = new DatosInvernaderoDTO(
                temperatura,
                humedadSuelo,
                humedadAire,
                luminosidad,
                caloventorActivo,
                humidificadorActivo,
                ventanalesAbiertos,
                persianasEnrolladas,
                bombaActiva
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.INTERCAMBIO_INVERNADERO,
                RabbitMQConfig.RUTA_TELEMETRIA,
                telemetria
        );

        log.info("Telemetría emitida -> T: {}°C, H_Suelo: {}%, H_Aire: {}%", temperatura, humedadSuelo, humedadAire);
    }
}