package com.chaykin.jasper.sample.module.model;

import lombok.Data;

@Data
public class RevenueItem {

    private String category;
    private Double amount;
    private Double percent;

    public RevenueItem(String category, Double amount, Double percent) {
        this.category = category;
        this.amount = amount;
        this.percent = percent;
    }

}

