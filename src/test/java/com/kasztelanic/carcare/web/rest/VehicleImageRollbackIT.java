package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.domain.Vehicle;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.repository.VehicleRepository;
import com.kasztelanic.carcare.service.VehicleService;
import com.kasztelanic.carcare.service.dto.VehicleDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * The one behaviour no class-{@code @Transactional} test in the suite can observe: what the
 * filesystem looks like after an {@code editVehicle} transaction commits or rolls back. Runs under
 * {@code NOT_SUPPORTED} so the service's own {@code @Transactional} boundary is the real one, and
 * induces a rollback by stubbing {@code VehicleRepository.save} to throw.
 */
@WithMockUser(username = "user")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VehicleImageRollbackIT extends AbstractImageIT {

    @Autowired
    private VehicleService vehicleService;

    @SpyBean
    private VehicleRepository vehicleRepository;

    @Test
    void rolledBackEditKeepsTheOldFileAndOrphansNoNewOne() throws Exception {
        Long id = null;
        String oldImage = null;
        AtomicReference<String> newImage = new AtomicReference<>();
        try {
            byte[] oldBytes = SessionFixtures.pngBytes();
            Vehicle vehicle = sessionFixtures.imageFor(sessionFixtures.vehicleFor("user"), oldBytes);
            id = vehicle.getId();
            oldImage = vehicle.getVehicleDetails().getImage();
            Path oldPath = imagePath(oldImage);
            assertThat(Files.exists(oldPath)).isTrue();

            // Arm the failure only after fixture setup — vehicleFor/imageFor both go through save().
            final Long vehicleId = id;
            doAnswer(invocation -> {
                Vehicle arg = invocation.getArgument(0);
                if (vehicleId.equals(arg.getId())) {
                    newImage.set(arg.getVehicleDetails().getImage());
                    throw new IllegalStateException("induced rollback");
                }
                return invocation.callRealMethod();
            }).when(vehicleRepository).save(any(Vehicle.class));

            VehicleDto edit = SessionFixtures.vehicleDtoWithImage("Rollback edit",
                SessionFixtures.jpegBytes(), "image/jpeg");
            // Spring's @Repository exception translation rewraps the induced failure, so match on
            // the message rather than the concrete type.
            assertThatThrownBy(() -> vehicleService.editVehicle(vehicleId, edit))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("induced rollback");

            assertThat(Files.exists(oldPath)).isTrue();
            assertThat(Files.readAllBytes(oldPath)).isEqualTo(oldBytes);
            assertThat(newImage.get()).isNotBlank();
            assertThat(Files.exists(imagePath(newImage.get()))).isFalse();
        } finally {
            Mockito.reset(vehicleRepository);
            if (id != null) {
                sessionFixtures.purgeRowsFor(List.of(id));
            }
            if (oldImage != null) {
                Files.deleteIfExists(imagePath(oldImage));
            }
            if (newImage.get() != null) {
                Files.deleteIfExists(imagePath(newImage.get()));
            }
        }
    }

    @Test
    void committedEditDeletesTheOldFile() throws Exception {
        Long id = null;
        String oldImage = null;
        String newImage = null;
        try {
            Vehicle vehicle = sessionFixtures.imageFor(sessionFixtures.vehicleFor("user"),
                SessionFixtures.pngBytes());
            id = vehicle.getId();
            oldImage = vehicle.getVehicleDetails().getImage();
            assertThat(Files.exists(imagePath(oldImage))).isTrue();

            VehicleDto edit = SessionFixtures.vehicleDtoWithImage("Commit edit",
                SessionFixtures.jpegBytes(), "image/jpeg");
            assertThat(vehicleService.editVehicle(id, edit)).isPresent();

            newImage = vehicleRepository.findById(id).orElseThrow().getVehicleDetails().getImage();
            assertThat(newImage).endsWith(".jpg");
            assertThat(Files.exists(imagePath(oldImage))).isFalse();
            assertThat(Files.exists(imagePath(newImage))).isTrue();
        } finally {
            if (id != null) {
                sessionFixtures.purgeRowsFor(List.of(id));
            }
            if (oldImage != null) {
                Files.deleteIfExists(imagePath(oldImage));
            }
            if (newImage != null) {
                Files.deleteIfExists(imagePath(newImage));
            }
        }
    }
}
