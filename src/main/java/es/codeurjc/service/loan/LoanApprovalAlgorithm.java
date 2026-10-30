package es.codeurjc.service.loan;

import es.codeurjc.model.LoanEvaluationResult;
import es.codeurjc.service.EuriborService;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LoanApprovalAlgorithm {
    private static final double BASE_INTEREST_RATE = 2.0;
    private static final double MAX_LIMIT = 0.4; // 40% of monthly income
    private final EuriborService euriborService;

    public LoanApprovalAlgorithm() {
        this.euriborService = new EuriborService();
    }

    public LoanApprovalAlgorithm(EuriborService euriborService) {
        this.euriborService = euriborService;
    }

    public LoanEvaluationResult evaluate(LoanRequest request) {
        // 2. Interest rate calculation - Raul
        double interestRate = BASE_INTEREST_RATE + euriborService.getEuribor();

        // 3. Installment calculation - Alejandro
        double installment = installmentCalculation(request.getAmount(), request.getTermMonths(), interestRate);

        // 1. Basic automatic Reject/Approve - Matias
        double amount = request.getAmount();
        if (amount < 1000 || amount > 50000) {
            return new LoanEvaluationResult(false, "Cantidad fuera de rango");
        }

        double time = request.getTermMonths();
        if (time < 6 || time > 120) {
            return new LoanEvaluationResult(false, "Plazo no válido");
        }

        if (amount * 0.2 > request.getCustomerBalance()) {
            return new LoanEvaluationResult(false, "Saldo insuficiente");
        }

        // 4. Installment limit - Carla
        if (!installmentLimit(request, interestRate, installment)) {
            return new LoanEvaluationResult(false, "Monthly installment exceeds 40% of income");
        }

        return new LoanEvaluationResult(true, "Loan approved", request.getAmount(), interestRate, 0);
    }

    // Alejandro
    public double installmentCalculation(double amount, double termMonth, double interestType) {
        double installment = amount * (1 + (interestType / 100)) / termMonth;
        BigDecimal resultRounded = new BigDecimal(installment).setScale(2, RoundingMode.HALF_UP);

        return resultRounded.doubleValue();
    }

    // Carla
    // InterestRate = 2% (base) + Euribor
    // Installment = (principal * (1 + interestRate/100)) / term
    // Limit: Installment <= 40% of monthly income
    public boolean installmentLimit(LoanRequest request, double interestRate, double installment) {
        double limit = MAX_LIMIT * request.getMonthlyIncome();
        return installment <= limit;
    }
}
