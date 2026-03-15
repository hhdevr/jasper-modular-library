package com.chaykin.jasper.sample.data;

import com.chaykin.jasper.sample.module.ExpenseModule;
import com.chaykin.jasper.sample.module.ModularFinancialReportModule;
import com.chaykin.jasper.sample.module.ProfitModule;
import com.chaykin.jasper.sample.module.RevenueModule;
import com.chaykin.jasper.sample.module.model.CompanyDetails;
import com.chaykin.jasper.sample.module.model.ExpenseItem;
import com.chaykin.jasper.sample.module.model.ProfitBreakdown;
import com.chaykin.jasper.sample.module.model.RevenueItem;

import java.util.List;

public class FinancialReportService {

    public static ModularFinancialReportModule buildModule() {
        CompanyDetails company = new CompanyDetails();
        company.setName("Acme Corp");
        company.setManager("John Smith");
        company.setEmail("john@acme.com");
        company.setAddress("123 Main St, New York");

        RevenueModule revenue = new RevenueModule(
                1_200_000.0, 15.5,
                List.of(new RevenueItem("Products", 800_000.0, 66.7),
                        new RevenueItem("Services", 350_000.0, 29.2),
                        new RevenueItem("Licensing", 50_000.0, 4.1)));

        ExpenseModule expense = new ExpenseModule(
                900_000.0, 10.2,
                List.of(new ExpenseItem("Salaries", 500_000.0, 55.6),
                        new ExpenseItem("Operations", 200_000.0, 22.2),
                        new ExpenseItem("Marketing", 120_000.0, 13.3),
                        new ExpenseItem("Infrastructure", 80_000.0, 8.9)));

        ProfitModule profit = new ProfitModule(
                350_000.0, 310_000.0, 280_000.0, 23.3,
                List.of(
                        new ProfitBreakdown("January", 380_000.0, 290_000.0, 90_000.0),
                        new ProfitBreakdown("February", 400_000.0, 300_000.0, 100_000.0),
                        new ProfitBreakdown("March", 420_000.0, 310_000.0, 110_000.0)));

        return new ModularFinancialReportModule(
                company,
                "Q1 2025",
                "USD",
                1_200_000.0,
                900_000.0,
                280_000.0,
                revenue,
                expense,
                profit
        );
    }
}
