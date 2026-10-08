package com.supportpilot.refund;

import com.supportpilot.config.ApproverAuth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
@Validated
public class RefundController {

    private final RefundService refunds;
    private final ApproverAuth approverAuth;

    public RefundController(RefundService refunds, ApproverAuth approverAuth) {
        this.refunds = refunds;
        this.approverAuth = approverAuth;
    }

    public record DecisionRequest(
            @NotBlank @Size(max = 100) String approverName,
            @Size(max = 1000) String note) {}

    /** Approver queue: all requests, or only one status (e.g. PENDING). */
    @GetMapping
    public List<RefundDto> list(
            @RequestHeader(value = ApproverAuth.HEADER, required = false) String key,
            @RequestParam(required = false) RefundStatus status) {
        approverAuth.require(key);
        return refunds.list(status).stream().map(RefundDto::from).toList();
    }

    /** A customer's own requests (demo: identified by email, see README). */
    @GetMapping("/mine")
    public List<RefundDto> mine(@RequestParam @Email @NotBlank String email) {
        return refunds.listForCustomer(email).stream().map(RefundDto::from).toList();
    }

    @PostMapping("/{id}/approve")
    public RefundDto approve(
            @RequestHeader(value = ApproverAuth.HEADER, required = false) String key,
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest body) {
        approverAuth.require(key);
        return RefundDto.from(refunds.approve(id, body.approverName().strip(), body.note()));
    }

    @PostMapping("/{id}/reject")
    public RefundDto reject(
            @RequestHeader(value = ApproverAuth.HEADER, required = false) String key,
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest body) {
        approverAuth.require(key);
        return RefundDto.from(refunds.reject(id, body.approverName().strip(), body.note()));
    }
}
