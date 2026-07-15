package com.choks.chokchok.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "metric")
public class Metric extends SignalRow {
}
