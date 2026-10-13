package gd.invernadero.simulador.service;

import gd.invernadero.simulador.config.RabbitMQConfig;
import gd.invernadero.simulador.dto.ComandoActuadorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class GestorActuadoresService {

    public record EstadoActuador (boolean modoAutomatico, boolean estadoManual) {}

    private final ConcurrentMap<String, EstadoActuador> mapaActuadores = new ConcurrentHashMap<>();

    public GestorActuadoresService() {
        mapaActuadores.put("caloventor", new EstadoActuador(true, false));
        mapaActuadores.put("humidificador", new EstadoActuador(true, false));
        mapaActuadores.put("ventanales", new EstadoActuador(true, false));
        mapaActuadores.put("persianas", new EstadoActuador(true, false));
        mapaActuadores.put("bomba", new EstadoActuador(true, false));
    }

    // Escucha las órdenes manuales emitidas por el backend principal desde la interfaz
    @RabbitListener(queues = RabbitMQConfig.COLA_COMANDOS)
    public void recibirComandoRemoto(ComandoActuadorDTO comando) {
        if (comando == null || comando.getNombreActuador() == null) return;

        String clave = comando.getNombreActuador().toLowerCase();
        EstadoActuador actual = mapaActuadores.get(clave);

        mapaActuadores.computeIfPresent(clave, (k, v) ->
                new EstadoActuador(comando.isModoAutomatico(), comando.isEstadoManual()));
    }
}