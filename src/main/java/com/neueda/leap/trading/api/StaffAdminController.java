package com.neueda.leap.trading.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.leap.trading.service.impl.StaffProvisioningService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/staff")
public class StaffAdminController {
    private final StaffProvisioningService staffProvisioningService;

    public StaffAdminController(StaffProvisioningService staffProvisioningService) {
        this.staffProvisioningService = staffProvisioningService;
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffRoleResponse grantRole(@Valid @RequestBody StaffProvisionRequest request) {
        return staffProvisioningService.grant(request);
    }

    @DeleteMapping("/{username}/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(@PathVariable String username, @PathVariable String role) {
        staffProvisioningService.revoke(username, role);
    }
}