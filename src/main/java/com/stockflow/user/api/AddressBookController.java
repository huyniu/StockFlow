package com.stockflow.user.api;

import com.stockflow.user.service.AddressBookService;
import com.stockflow.user.domain.User;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressBookController {
    private final AddressBookService service;
    public AddressBookController(AddressBookService service) { this.service=service; }
    @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal User user) { return service.list(user.getId()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@AuthenticationPrincipal User user,@Valid @RequestBody AddressBookService.Request request) {return service.save(user.getId(),null,request);}
    @PutMapping("/{id}") public Map<String,Object> update(@AuthenticationPrincipal User user,@PathVariable Long id,@Valid @RequestBody AddressBookService.Request request) {return service.save(user.getId(),id,request);}
    @PostMapping("/{id}/default") public Map<String,Object> makeDefault(@AuthenticationPrincipal User user,@PathVariable Long id) {return service.makeDefault(user.getId(),id);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal User user,@PathVariable Long id) {service.delete(user.getId(),id);}
}
