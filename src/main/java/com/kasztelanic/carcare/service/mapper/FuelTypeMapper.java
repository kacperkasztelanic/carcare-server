package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.FuelType;
import com.kasztelanic.carcare.repository.FuelTypeRepository;
import com.kasztelanic.carcare.service.dto.FuelTypeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class FuelTypeMapper {

    private final FuelTypeRepository fuelTypeRepository;

    @Autowired
    public FuelTypeMapper(FuelTypeRepository fuelTypeRepository) {
        this.fuelTypeRepository = fuelTypeRepository;
    }

    public FuelTypeDto fuelTypeToFuelTypeDto(FuelType fuelType, Locale locale) {
        return FuelTypeDto.of(fuelType.getType(), selectTranslation(fuelType, locale));
    }

    public FuelType fuelTypeDtoToFuelType(FuelTypeDto fuelTypeDto) {
        return fuelTypeRepository.findByType(fuelTypeDto.getType())//
            .orElseThrow(IllegalStateException::new);
    }

    private static String selectTranslation(FuelType fuelType, Locale locale) {
        if (locale.getLanguage().equals(Locale.forLanguageTag("pl").getLanguage())) {
            return fuelType.getPolishTranslation();
        }
        return fuelType.getEnglishTranslation();
    }
}
