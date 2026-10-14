package gd.invernadero.simulador.service;

import gd.invernadero.simulador.config.RabbitMQConfig;
import gd.invernadero.simulador.dto.ComandoActuadorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class GestorActuadoresService {

    public record EstadoActuador(boolean modoAutomatico, boolean estadoManual) {}

    private static final EstadoActuador AUTOMATICO = new EstadoActuador(true, false);

    private final ConcurrentMap<String, EstadoActuador> mapaActuadores = new ConcurrentHashMap<>();

    public GestorActuadoresService() {
        List.of("caloventor", "humidificador", "ventanales", "persianas", "bomba")
                .forEach(n -> mapaActuadores.put(n, AUTOMATICO));
    }

    @RabbitListener(queues = RabbitMQConfig.COLA_COMANDOS)
    public void recibirComandoRemoto(ComandoActuadorDTO comando) {
        if (comando == null || comando.getNombreActuador() == null) {
            log.warn("Comando descartado: sin nombre de actuador");
            return;
        }
        String clave = comando.getNombreActuador().trim().toLowerCase(Locale.ROOT);
        EstadoActuador nuevo = new EstadoActuador(comando.isModoAutomatico(), comando.isEstadoManual());
        if (mapaActuadores.replace(clave, nuevo) == null) {   // null = clave inexistente
            log.warn("Actuador desconocido '{}'; comando descartado", comando.getNombreActuador());
            return;
        }
        log.info("Actuador [{}] configurado: automático={}, estado manual={}",
                clave, nuevo.modoAutomatico(), nuevo.estadoManual());
    }

    public boolean resolverEstado(String nombreActuador, boolean condicionAutomatica) {
        EstadoActuador cfg = mapaActuadores.get(nombreActuador.toLowerCase(Locale.ROOT));
        return (cfg == null || cfg.modoAutomatico()) ? condicionAutomatica : cfg.estadoManual();
    }
}