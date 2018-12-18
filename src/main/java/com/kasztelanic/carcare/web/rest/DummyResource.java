package com.kasztelanic.carcare.web.rest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.domain.User;
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
        User user = userService.getUserWithAuthorities().orElseThrow(IllegalStateException::new);
        VehicleDetails vehicleDetails = VehicleDetails.builder().enginePower(120).engineVolume(2500)
                .notes("Bought in 2015").vinNumber("123-456-789").weight(1980).yearOfManufacture(2012).build();
        Vehicle v = Vehicle.builder().make("BMW").model("520d").licensePlate("DW 1234").fuelType(FuelType.of("DIESEL"))
                .inspection(new HashSet<>()).refuel(new HashSet<>()).repair(new HashSet<>()).routineService(new HashSet<>()).owner(user)
                .vehicleDetails(vehicleDetails).insurance(new HashSet<>()).build();
//        v.addInsurance(Insurance.builder().costInCents(75000).details("OC").validFrom(LocalDate.of(2017, 12, 20))
//                .validThru(LocalDate.of(2018, 12, 20)).vehicleEvent(VehicleEvent.of(120000, LocalDate.of(2017, 11, 19)))
//                .build());
        return vehicleRepository.save(v);
    }

    @GetMapping("/bar")
    @Transactional
    public Collection<Vehicle> bar() {
        System.out.println("bar");
        List<Vehicle> list = vehicleRepository.findByOwnerIsCurrentUser();
        list.forEach(v -> System.out.println(v.getInsurance()));
        return list;
    }
}
