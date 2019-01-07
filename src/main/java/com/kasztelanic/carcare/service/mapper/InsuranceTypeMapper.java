package com.kasztelanic.carcare.service.mapper;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.domain.InsuranceType;
import com.kasztelanic.carcare.repository.InsuranceTypeRepository;
import com.kasztelanic.carcare.service.dto.InsuranceTypeDto;

@Service
public class InsuranceTypeMapper {

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    public InsuranceTypeDto insuranceTypeToInsuranceTypeDto(InsuranceType insuranceType, Locale locale) {
        return InsuranceTypeDto.of(insuranceType.getType(), selectTranslation(insuranceType, locale));
    }

    public InsuranceType insuranceTypeDtoToInsuranceType(InsuranceTypeDto insuranceTypeDto) {
        return insuranceTypeRepository.findByType(insuranceTypeDto.getType()).orElseThrow(IllegalStateException::new);
    }

    private static String selectTranslation(InsuranceType insuranceType, Locale locale) {
        if (locale.getLanguage().equals(Locale.forLanguageTag("pl").getLanguage())) {
            return insuranceType.getPolishTranslation();
        }
        return insuranceType.getEnglishTranslation();
    }
}
