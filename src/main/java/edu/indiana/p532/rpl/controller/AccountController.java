package edu.indiana.p532.rpl.controller;

import edu.indiana.p532.rpl.domain.ResourceKind;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.domain.operational.Account;
import edu.indiana.p532.rpl.domain.operational.Entry;
import edu.indiana.p532.rpl.dto.AccountDto;
import edu.indiana.p532.rpl.dto.EntryDto;
import edu.indiana.p532.rpl.manager.LedgerManager;
import edu.indiana.p532.rpl.repository.ResourceTypeRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final LedgerManager ledgerManager;
    private final ResourceTypeRepository resourceTypeRepository;

    public AccountController(LedgerManager ledgerManager, ResourceTypeRepository resourceTypeRepository) {
        this.ledgerManager = ledgerManager;
        this.resourceTypeRepository = resourceTypeRepository;
    }

    @GetMapping
    public List<AccountDto> listAccounts() {
        return ledgerManager.getAllAccounts().stream().map(this::toAccountDto).toList();
    }

    @PostMapping("/{id}/deposit")
    public AccountDto deposit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.getOrDefault("description", "Stock deposit").toString();
        ledgerManager.depositToPool(id, amount, description);
        return toAccountDto(ledgerManager.getAccountById(id));
    }

    @GetMapping("/{id}/entries")
    public List<EntryDto> getEntries(@PathVariable Long id) {
        return ledgerManager.getEntriesForAccount(id).stream()
                .map(this::toDto)
                .toList();
    }

    private AccountDto toAccountDto(Account acc) {
        BigDecimal balance = ledgerManager.getBalance(acc.getId());
        ResourceKind rk = acc.getResourceTypeId() == null ? null
                : resourceTypeRepository.findById(acc.getResourceTypeId())
                        .map(ResourceType::getKind).orElse(null);
        return new AccountDto(acc.getId(), acc.getName(), acc.getKind().name(),
                balance, balance.compareTo(BigDecimal.ZERO) < 0, rk);
    }

    private EntryDto toDto(Entry e) {
        return new EntryDto(
                e.getId(),
                e.getAccount().getId(),
                e.getAccount().getName(),
                e.getAmount(),
                e.getChargedAt().toString(),
                e.getBookedAt().toString(),
                e.getTransaction().getOriginatingActionId(),
                e.getDescription()
        );
    }
}
