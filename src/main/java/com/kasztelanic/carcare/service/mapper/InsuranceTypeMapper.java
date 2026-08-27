package com.kasztelanic.carcare.service.mapper;

import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.service.dto.InsuranceTypeDto;
import com.kasztelanic.carcare.service.exception.InvalidLookupTypeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class InsuranceTypeMapper {

    private final InsuranceTypeRepository insuranceTypeRepository;

    @Autowired
    public InsuranceTypeMapper(InsuranceTypeRepository insuranceTypeRepository) {
        this.insuranceTypeRepository = insuranceTypeRepository;
    }

    public InsuranceTypeDto insuranceTypeToInsuranceTypeDto(InsuranceType insuranceType, Locale locale) {
        return InsuranceTypeDto.of(insuranceType.getType(), selectTranslation(insuranceType, locale));
    }

    public InsuranceType insuranceTypeDtoToInsuranceType(InsuranceTypeDto insuranceTypeDto) {
        if (insuranceTypeDto == null) {
            throw new InvalidLookupTypeException("insuranceType");
        }
        return insuranceTypeRepository.findByType(insuranceTypeDto.getType())//
            .orElseThrow(() -> new InvalidLookupTypeException("insuranceType"));
    }

    private static String selectTranslation(InsuranceType insuranceType, Locale locale) {
        if (locale.getLanguage().equals(Locale.forLanguageTag("pl").getLanguage())) {
            return insuranceType.getPolishTranslation();
        }
        return insuranceType.getEnglishTranslation();
    }
}
