package gd.invernadero.simulador.service;

import gd.invernadero.simulador.config.RabbitMQConfig;
import gd.invernadero.simulador.dto.ConfiguracionUmbralesDTO;
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
    public void recibirNuevosUmbrales(ConfiguracionUmbralesDTO n) {
        if (!esValido(n)) {
            log.warn("Umbrales inválidos descartados: {}", n);
            return;
        }
        if (n.equals(this.umbrales)) return;   // re-publicación periódica sin cambios
        this.umbrales = n;
        log.info("Simulador recalibrado con nuevos umbrales: {}", n);
    }

    private boolean esValido(ConfiguracionUmbralesDTO n) {
        return n != null
                && n.getTempMin() != null && n.getTempMax() != null
                && n.getHumSueloMin() != null && n.getHumSueloMax() != null
                && n.getHumAireMin() != null && n.getHumAireMax() != null
                && n.getLuzMin() != null && n.getLuzMax() != null
                && n.getTempMin() < n.getTempMax()
                && n.getHumSueloMin() < n.getHumSueloMax()
                && n.getHumAireMin() < n.getHumAireMax()
                && n.getLuzMin() < n.getLuzMax();
    }
}
