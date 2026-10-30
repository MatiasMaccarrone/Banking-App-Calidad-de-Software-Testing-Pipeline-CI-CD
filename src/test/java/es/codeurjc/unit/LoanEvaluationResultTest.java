package es.codeurjc.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import es.codeurjc.service.EuriborService;
import es.codeurjc.service.loan.LoanApprovalAlgorithm;
import es.codeurjc.service.loan.LoanRequest;
import es.codeurjc.model.LoanEvaluationResult;

/*

1. Basic automatic Reject/Approve - Matias
2. Interest rate calculation - Raul
3. Installment calculation - Alejandro
4. Installment limit - Carla

*/

public class LoanEvaluationResultTest {
    private EuriborService euriborService;
    private LoanApprovalAlgorithm algorithm;

    private static Stream<Arguments> provideLoanRequestsForApprovalRejection() {
        return Stream.of(
                Arguments.of(new LoanRequest(500, 24, 5000, 5000), false, "Cantidad fuera de rango"),
                Arguments.of(new LoanRequest(60000, 24, 5000, 5000), false, "Cantidad fuera de rango"),
                Arguments.of(new LoanRequest(20000, 4, 5000, 5000), false, "Plazo no válido"),
                Arguments.of(new LoanRequest(20000, 240, 5000, 5000), false, "Plazo no válido"),
                Arguments.of(new LoanRequest(20000, 24, 5000, 500), false, "Saldo insuficiente"),
                Arguments.of(new LoanRequest(20000, 24, 5000, 5000), true, "Loan approved"));
    }

    private static Stream<Arguments> provideInterestRateCases() {
        return Stream.of(
                Arguments.of(3.0, 5.0),
                Arguments.of(5.0, 7.0)
        );
    }

    private static Stream<Arguments> provideLoanRequestsForInstallmentCalculation() {
        return Stream.of(
                Arguments.of(new LoanRequest(20000, 24, 5000, 5000), 3.0, 875),
                Arguments.of(new LoanRequest(20000, 24, 5000, 5000), 3.5, 879.17),
                Arguments.of(new LoanRequest(20000, 24, 5000, 5000), 5.0, 891.67));
    }

    private static Stream<Arguments> provideLoanRequestsForInstallmentLimit() {
        return Stream.of(
                Arguments.of(new LoanRequest(20000, 24, 2000, 5000), 3.0, false,
                        "Monthly installment exceeds 40% of income"),
                Arguments.of(new LoanRequest(20000, 24, 3000, 5000), 3.0, true, "Loan approved"),
                Arguments.of(new LoanRequest(20000, 7, 3000, 5000), 5.0, false,
                        "Monthly installment exceeds 40% of income"));
    }

    @BeforeEach
    public void setUp() {
        euriborService = mock(EuriborService.class);
        algorithm = new LoanApprovalAlgorithm(euriborService);
    }

    // 1. Basic automatic Reject/Approve - Matias
    @ParameterizedTest
    @MethodSource("provideLoanRequestsForApprovalRejection")
    public void loanApprovalRejectionTest(LoanRequest request, boolean expectedApproval, String expectedReason) {
        // Given
        when(euriborService.getEuribor()).thenReturn(3.0);

        // When
        LoanEvaluationResult result = algorithm.evaluate(request);

        // Then
        assertEquals(expectedApproval, result.isApproved());
        assertEquals(expectedReason, result.getReason());
    }

    
    // 2. Interest rate calculation - Raul
    @ParameterizedTest
    @MethodSource("provideInterestRateCases")
    public void interestRateCalculationTest(double euribor, double expectedInterestRate) {
        // Given
        LoanRequest request = new LoanRequest();
        request.setAmount(20000);
        request.setTermMonths(24);
        request.setCustomerBalance(5000);
        request.setMonthlyIncome(5000);

        when(euriborService.getEuribor()).thenReturn(euribor);

        // When
        LoanEvaluationResult result = algorithm.evaluate(request);

        // Then
        assertEquals(expectedInterestRate, result.getInterestRate());
    }



    // 3. Installment calculation - Alejandro
    @Test
    public void installmentCalculation875Test(){
        // Given
        LoanRequest request = new LoanRequest();
        request.setAmount(20000);
        request.setTermMonths(24);
        request.setCustomerBalance(5000);
        request.setMonthlyIncome(5000);

        when(euriborService.getEuribor()).thenReturn(3.0);

        // When
        LoanEvaluationResult result = algorithm.evaluate(request);

        // Then
        assertEquals(875, algorithm.installmentCalculation(request.getAmount(), request.getTermMonths() , result.getInterestRate()));
    }

    @ParameterizedTest
    @MethodSource("provideLoanRequestsForInstallmentCalculation")
    public void installmentCalculationTest(LoanRequest loanRequest, double euribor, double expectedResult) {
        // Given
        when(euriborService.getEuribor()).thenReturn(euribor);

        // When
        LoanEvaluationResult result = algorithm.evaluate(loanRequest);

        // Then
        assertEquals(expectedResult, algorithm.installmentCalculation(loanRequest.getAmount(),
                loanRequest.getTermMonths(), result.getInterestRate()));
    }


    // 4. Installment limit - Carla
    @ParameterizedTest
    @MethodSource("provideLoanRequestsForInstallmentLimit")
    public void installmentLimitTest(LoanRequest loanRequest, double euribor, boolean expectedApproval,
            String expectedReason) {
        // Given
        when(euriborService.getEuribor()).thenReturn(euribor);

        // When
        LoanEvaluationResult loanEvaluationResult = algorithm.evaluate(loanRequest);

        // Then
        assertEquals(expectedApproval, loanEvaluationResult.isApproved());
        assertEquals(expectedReason, loanEvaluationResult.getReason());
    }
}
