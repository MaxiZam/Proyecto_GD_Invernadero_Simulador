package gd.invernadero.simulador.service;

import gd.invernadero.simulador.dto.ConfiguracionUmbralesDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GestorUmbralesServiceTest {

    @Test
    void ignoraUmbralesInvalidos() {
        var g = new GestorUmbralesService();
        var original = g.getUmbrales();

        g.recibirNuevosUmbrales(new ConfiguracionUmbralesDTO(30.0, 20.0, 50.0, 80.0, 50.0, 75.0, 400, 900)); // min > max
        g.recibirNuevosUmbrales(null);

        assertSame(original, g.getUmbrales());
    }

    @Test
    void aceptaUmbralesValidos() {
        var g = new GestorUmbralesService();
        var original = g.getUmbrales();

        g.recibirNuevosUmbrales(new ConfiguracionUmbralesDTO(15.0, 30.0, 40.0, 85.0, 45.0, 80.0, 300, 950));

        assertNotSame(original, g.getUmbrales());
    }
}
