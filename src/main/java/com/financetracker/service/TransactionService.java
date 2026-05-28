package com.financetracker.service;

import com.financetracker.entity.Account;
import com.financetracker.entity.Transaction;
import com.financetracker.entity.TransactionType;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.exception.UnauthorizedException;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                               AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Transaction create(UUID userId, UUID accountId, BigDecimal amount,
                               TransactionType type, String category,
                               String description, LocalDate date) {
        log.debug("Creating {} transaction: amount={}, category='{}', accountId={}, userId={}",
                type, amount, category, accountId, userId);

        // findByIdAndUser_Id checks existence AND ownership in a single query
        Account account = accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "Account not found or does not belong to this user"));

        Transaction saved = transactionRepository.save(Transaction.builder()
                .account(account).amount(amount).type(type)
                .category(category).description(description).date(date)
                .build());

        log.info("Transaction created: id={}, type={}, amount={}, category='{}', userId={}",
                saved.getId(), type, amount, category, userId);
        return saved;
    }

    public List<Transaction> getByDateRange(UUID userId, LocalDate start, LocalDate end) {
        List<Transaction> results = transactionRepository.findByAccount_User_IdAndDateBetween(userId, start, end);
        log.debug("Fetched {} transactions for userId={} between {} and {}", results.size(), userId, start, end);
        return results;
    }

    public List<Transaction> getByCategory(UUID userId, String category) {
        List<Transaction> results = transactionRepository.findByAccount_User_IdAndCategory(userId, category);
        log.debug("Fetched {} transactions for userId={} in category='{}'", results.size(), userId, category);
        return results;
    }

    public Transaction findById(UUID transactionId, UUID userId) {
        return transactionRepository.findByIdAndAccount_User_Id(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }

    @Transactional
    public Transaction update(UUID transactionId, UUID userId, BigDecimal amount,
                               TransactionType type, String category,
                               String description, LocalDate date) {
        log.debug("Updating transaction id={}, userId={}", transactionId, userId);

        Transaction transaction = findById(transactionId, userId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setCategory(category);
        transaction.setDescription(description);
        transaction.setDate(date);
        Transaction saved = transactionRepository.save(transaction);

        log.info("Transaction updated: id={}, type={}, amount={}, category='{}', userId={}",
                transactionId, type, amount, category, userId);
        return saved;
    }

    @Transactional
    public void delete(UUID transactionId, UUID userId) {
        log.debug("Deleting transaction id={}, userId={}", transactionId, userId);

        Transaction transaction = findById(transactionId, userId);
        transactionRepository.delete(transaction);

        log.info("Transaction deleted: id={}, userId={}", transactionId, userId);
    }
}
