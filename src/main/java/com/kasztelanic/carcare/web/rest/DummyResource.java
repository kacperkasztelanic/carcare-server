package com.kasztelanic.carcare.web.rest;

import java.util.Collection;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.domain.VehicleDetails;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.UserService;

@RestController
@RequestMapping("/api/dummy")
public class DummyResource {

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @GetMapping("/foo")
    public Vehicle foo() {
        Vehicle v = Vehicle.builder().make("BMW").model("520d").licensePlate("DW 1234")
                .owner(userService.getUserWithAuthorities().get()).vehicleDetails(new VehicleDetails()).build();
        v = vehicleRepository.save(v);
        return v;
    }

    @GetMapping("/bar")
    @Transactional
    public Collection<Vehicle> bar() {
        List<Vehicle> list = vehicleRepository.findByOwnerIsCurrentUser();
        System.out.println(list.get(0).getVehicleDetails());
        return list;
    }
}
