package gd.invernadero.simulador.service;

import gd.invernadero.simulador.dto.ComandoActuadorDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestorActuadoresServiceTest {

    @Test
    void manualTienePrioridadYSePuedeRevertir() {
        var g = new GestorActuadoresService();
        assertTrue(g.resolverEstado("bomba", true));                          // automático

        g.recibirComandoRemoto(new ComandoActuadorDTO("bomba", false, false));
        assertFalse(g.resolverEstado("bomba", true));                         // manual apagado gana

        g.recibirComandoRemoto(new ComandoActuadorDTO("BOMBA ", true, false));
        assertTrue(g.resolverEstado("bomba", true));                          // vuelve a automático
    }

    @Test
    void actuadorDesconocidoNoAfectaAlResto() {
        var g = new GestorActuadoresService();
        g.recibirComandoRemoto(new ComandoActuadorDTO("luz", false, true));
        assertTrue(g.resolverEstado("caloventor", true));
    }
}
