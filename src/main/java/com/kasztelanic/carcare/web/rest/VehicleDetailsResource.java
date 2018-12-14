//package com.kasztelanic.carcare.web.rest;
//
//import com.codahale.metrics.annotation.Timed;
//import com.kasztelanic.carcare.domain.VehicleDetails;
//import com.kasztelanic.carcare.repository.VehicleDetailsRepository;
//import com.kasztelanic.carcare.web.rest.errors.BadRequestAlertException;
//import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
//import io.github.jhipster.web.util.ResponseUtil;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import javax.validation.Valid;
//import java.net.URI;
//import java.net.URISyntaxException;
//
//import java.util.List;
//import java.util.Optional;
//
///**
// * REST controller for managing VehicleDetails.
// */
//@RestController
//@RequestMapping("/api")
//public class VehicleDetailsResource {
//
//    private final Logger log = LoggerFactory.getLogger(VehicleDetailsResource.class);
//
//    private static final String ENTITY_NAME = "vehicleDetails";
//
//    private VehicleDetailsRepository vehicleDetailsRepository;
//
//    public VehicleDetailsResource(VehicleDetailsRepository vehicleDetailsRepository) {
//        this.vehicleDetailsRepository = vehicleDetailsRepository;
//    }
//
//    /**
//     * POST  /vehicle-details : Create a new vehicleDetails.
//     *
//     * @param vehicleDetails the vehicleDetails to create
//     * @return the ResponseEntity with status 201 (Created) and with body the new vehicleDetails, or with status 400 (Bad Request) if the vehicleDetails has already an ID
//     * @throws URISyntaxException if the Location URI syntax is incorrect
//     */
//    @PostMapping("/vehicle-details")
//    @Timed
//    public ResponseEntity<VehicleDetails> createVehicleDetails(@Valid @RequestBody VehicleDetails vehicleDetails) throws URISyntaxException {
//        log.debug("REST request to save VehicleDetails : {}", vehicleDetails);
//        if (vehicleDetails.getId() != null) {
//            throw new BadRequestAlertException("A new vehicleDetails cannot already have an ID", ENTITY_NAME, "idexists");
//        }
//        VehicleDetails result = vehicleDetailsRepository.save(vehicleDetails);
//        return ResponseEntity.created(new URI("/api/vehicle-details/" + result.getId()))
//            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
//            .body(result);
//    }
//
//    /**
//     * PUT  /vehicle-details : Updates an existing vehicleDetails.
//     *
//     * @param vehicleDetails the vehicleDetails to update
//     * @return the ResponseEntity with status 200 (OK) and with body the updated vehicleDetails,
//     * or with status 400 (Bad Request) if the vehicleDetails is not valid,
//     * or with status 500 (Internal Server Error) if the vehicleDetails couldn't be updated
//     * @throws URISyntaxException if the Location URI syntax is incorrect
//     */
//    @PutMapping("/vehicle-details")
//    @Timed
//    public ResponseEntity<VehicleDetails> updateVehicleDetails(@Valid @RequestBody VehicleDetails vehicleDetails) throws URISyntaxException {
//        log.debug("REST request to update VehicleDetails : {}", vehicleDetails);
//        if (vehicleDetails.getId() == null) {
//            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
//        }
//        VehicleDetails result = vehicleDetailsRepository.save(vehicleDetails);
//        return ResponseEntity.ok()
//            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, vehicleDetails.getId().toString()))
//            .body(result);
//    }
//
//    /**
//     * GET  /vehicle-details : get all the vehicleDetails.
//     *
//     * @return the ResponseEntity with status 200 (OK) and the list of vehicleDetails in body
//     */
//    @GetMapping("/vehicle-details")
//    @Timed
//    public List<VehicleDetails> getAllVehicleDetails() {
//        log.debug("REST request to get all VehicleDetails");
//        return vehicleDetailsRepository.findAll();
//    }
//
//    /**
//     * GET  /vehicle-details/:id : get the "id" vehicleDetails.
//     *
//     * @param id the id of the vehicleDetails to retrieve
//     * @return the ResponseEntity with status 200 (OK) and with body the vehicleDetails, or with status 404 (Not Found)
//     */
//    @GetMapping("/vehicle-details/{id}")
//    @Timed
//    public ResponseEntity<VehicleDetails> getVehicleDetails(@PathVariable Long id) {
//        log.debug("REST request to get VehicleDetails : {}", id);
//        Optional<VehicleDetails> vehicleDetails = vehicleDetailsRepository.findById(id);
//        return ResponseUtil.wrapOrNotFound(vehicleDetails);
//    }
//
//    /**
//     * DELETE  /vehicle-details/:id : delete the "id" vehicleDetails.
//     *
//     * @param id the id of the vehicleDetails to delete
//     * @return the ResponseEntity with status 200 (OK)
//     */
//    @DeleteMapping("/vehicle-details/{id}")
//    @Timed
//    public ResponseEntity<Void> deleteVehicleDetails(@PathVariable Long id) {
//        log.debug("REST request to delete VehicleDetails : {}", id);
//
//        vehicleDetailsRepository.deleteById(id);
//        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
//    }
//}
