package com.financetracker.service;

import com.financetracker.entity.Account;
import com.financetracker.entity.AccountType;
import com.financetracker.entity.User;
import com.financetracker.exception.ResourceNotFoundException;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Account create(UUID userId, String name, AccountType type) {
        log.debug("Creating {} account '{}' for userId={}", type, name, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Account saved = accountRepository.save(Account.builder()
                .user(user).name(name).type(type).balance(BigDecimal.ZERO)
                .build());

        log.info("Account created: id={}, name='{}', type={}, userId={}", saved.getId(), name, type, userId);
        return saved;
    }

    public List<Account> getByUser(UUID userId) {
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        log.debug("Fetched {} accounts for userId={}", accounts.size(), userId);
        return accounts;
    }

    public BigDecimal calculateNetWorth(UUID userId) {
        BigDecimal netWorth = accountRepository.sumBalanceByUserId(userId);
        log.debug("Net worth for userId={}: {}", userId, netWorth);
        return netWorth;
    }

    public Account findById(UUID accountId, UUID userId) {
        return accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    @Transactional
    public Account update(UUID accountId, UUID userId, String name, AccountType type) {
        log.debug("Updating account id={}, userId={}", accountId, userId);

        Account account = findById(accountId, userId);
        account.setName(name);
        account.setType(type);
        Account saved = accountRepository.save(account);

        log.info("Account updated: id={}, name='{}', type={}, userId={}", accountId, name, type, userId);
        return saved;
    }

    @Transactional
    public void delete(UUID accountId, UUID userId) {
        log.debug("Deleting account id={}, userId={}", accountId, userId);

        Account account = findById(accountId, userId);
        accountRepository.delete(account);

        log.info("Account deleted: id={}, userId={}", accountId, userId);
    }
}
