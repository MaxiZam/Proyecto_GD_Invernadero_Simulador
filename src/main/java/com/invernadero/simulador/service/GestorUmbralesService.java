package com.invernadero.simulador.service;

import com.invernadero.simulador.config.RabbitMQConfig;
import com.invernadero.simulador.dto.ConfiguracionUmbralesDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Getter
@Slf4j
@Service
public class GestorUmbralesService {

    private volatile ConfiguracionUmbralesDTO umbrales =
            new ConfiguracionUmbralesDTO(18.0, 28.0, 50.0, 80.0, 50.0, 75.0, 400, 900);

    @RabbitListener(queues = RabbitMQConfig.COLA_UMBRALES)
    public void recibirNuevosUmbrales(ConfiguracionUmbralesDTO nuevosUmbrales) {
        this.umbrales = nuevosUmbrales;
        log.info("Simulador recalibrado con nuevos umbrales: {}", nuevosUmbrales);
    }

}
