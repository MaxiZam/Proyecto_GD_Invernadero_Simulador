package gd.invernadero.simulador.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionUmbralesDTO implements Serializable {
    // Temperatura (°C)
    private Double tempMin;
    private Double tempMax;

    // Humedad del Suelo (%)
    private Double humSueloMin;
    private Double humSueloMax;

    // Humedad del Aire (%)
    private Double humAireMin;
    private Double humAireMax;

    // Luminosidad (Lux)
    private Integer luzMin;
    private Integer luzMax;
}
