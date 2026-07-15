package com.choks.chokchok.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "log")
public class Log extends SignalRow {
}
