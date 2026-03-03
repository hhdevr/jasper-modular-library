package com.chaykin.jasper.sample.module.model;

import lombok.Data;

@Data
public class ProfitBreakdown {

    private String month;
    private Double revenue;
    private Double expenses;
    private Double profit;

    public ProfitBreakdown(String month, Double revenue,
                           Double expenses, Double profit) {
        this.month = month;
        this.revenue = revenue;
        this.expenses = expenses;
        this.profit = profit;
    }

}
