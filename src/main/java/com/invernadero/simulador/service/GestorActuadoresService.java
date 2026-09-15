package com.invernadero.simulador.service;

import com.invernadero.simulador.config.RabbitMQConfig;
import com.invernadero.simulador.dto.ComandoActuadorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class GestorActuadoresService {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstadoActuador {
        private volatile boolean modoAutomatico = true;
        private volatile boolean estadoManual = false;
    }

    private final ConcurrentMap<String, EstadoActuador> mapaActuadores = new ConcurrentHashMap<>();

    public GestorActuadoresService() {
        mapaActuadores.put("caloventor", new EstadoActuador());
        mapaActuadores.put("humidificador", new EstadoActuador());
        mapaActuadores.put("ventanales", new EstadoActuador());
        mapaActuadores.put("persianas", new EstadoActuador());
        mapaActuadores.put("bomba", new EstadoActuador());
    }

    // Escucha las órdenes manuales emitidas por el backend principal desde la interfaz
    @RabbitListener(queues = RabbitMQConfig.COLA_COMANDOS)
    public void recibirComandoRemoto(ComandoActuadorDTO comando) {
        if (comando == null || comando.getNombreActuador() == null) return;

        String clave = comando.getNombreActuador().toLowerCase();
        EstadoActuador actual = mapaActuadores.get(clave);

        if (actual != null) {
            actual.setModoAutomatico(comando.isModoAutomatico());
            actual.setEstadoManual(comando.isEstadoManual());
            log.info("Actuador [{}] configurado externamente: Automático={}, Estado={}",
                    clave, comando.isModoAutomatico(), comando.isEstadoManual());
        }
    }

    // Decide si aplica la regla ambiental o el mandato forzado del usuario
    public boolean resolverEstado(String nombreActuador, boolean condicionAutomatica) {
        EstadoActuador config = mapaActuadores.get(nombreActuador.toLowerCase());
        if (config == null || config.isModoAutomatico()) {
            return condicionAutomatica;
        }
        return config.isEstadoManual();
    }
}