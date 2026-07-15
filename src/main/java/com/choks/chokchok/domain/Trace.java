package com.choks.chokchok.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "trace")
public class Trace extends SignalRow {
}
