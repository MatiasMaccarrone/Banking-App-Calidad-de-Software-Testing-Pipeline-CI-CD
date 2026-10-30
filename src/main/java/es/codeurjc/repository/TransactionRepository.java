package es.codeurjc.repository;

import es.codeurjc.model.Account;
import es.codeurjc.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

import java.util.List;

/**
 * Repository for Transaction entity.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    List<Transaction> findByAccount(Account account);
    
    List<Transaction> findByAccountOrderByTimestampDesc(Account account);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.account = :account AND t.type = 'WITHDRAWAL' AND t.timestamp >= :since")
    double sumWithdrawalsAfter(@Param("account") Account account, @Param("since") LocalDateTime since);
}
