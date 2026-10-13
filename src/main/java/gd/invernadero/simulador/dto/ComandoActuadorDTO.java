package gd.invernadero.simulador.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComandoActuadorDTO implements Serializable {
    private String nombreActuador;  // "caloventor", "humidificador", "ventanales", "persianas", "bomba"
    private boolean modoAutomatico; // true: autónomo por regla, false: manual
    private boolean estadoManual;    // true: encendido/abierto, false: apagado/cerrado
}