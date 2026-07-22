package com.choks.chokchok.repository;

import com.choks.chokchok.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByCompanyCode(String companyCode);
}
