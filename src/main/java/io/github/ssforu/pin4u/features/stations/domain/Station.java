package io.github.ssforu.pin4u.features.stations.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "stations")
@Getter @Setter
public class Station {
    @Id
    @Column(length = 16)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String line;

    @Column(nullable = false, precision = 11, scale = 7)
    private BigDecimal lat;

    @Column(nullable = false, precision = 11, scale = 7)
    private BigDecimal lng;
}
