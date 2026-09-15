package com.invernadero.simulador.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatosInvernaderoDTO implements Serializable {
    private Double temperatura;
    private Double humedadSuelo;
    private Double humedadAire;
    private Integer luminosidad;
    private boolean caloventorActivo;
    private boolean humidificadorActivo;
    private boolean ventanalesAbiertos;
    private boolean persianasEnrolladas;
    private boolean bombaActiva;
}