package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sacco.mvp.domain.LoanProductStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductSettingRepository extends JpaRepository<LoanProductSetting, UUID> {
    Optional<LoanProductSetting> findBySaccoIdAndLoanTypeAndActiveTrue(String saccoId, LoanType loanType);

    Optional<LoanProductSetting> findBySaccoIdAndLoanType(String saccoId, LoanType loanType);

    Optional<LoanProductSetting> findByIdAndSaccoId(UUID id, String saccoId);

    List<LoanProductSetting> findBySaccoIdAndIdIn(String saccoId, java.util.Collection<UUID> ids);

    @Query("""
        select p from LoanProductSetting p
        where p.saccoId = :saccoId
          and (p.productStatus is null or p.productStatus not in :excludedStatuses)
          and (:search = ''
            or lower(coalesce(p.productCode, '')) like concat(:search, '%')
            or lower(coalesce(p.productName, '')) like concat(:search, '%'))
        order by coalesce(p.displayOrder, 2147483647), lower(coalesce(p.productName, '')), p.id
        """)
    Page<LoanProductSetting> findConfigurationPage(@Param("saccoId") String saccoId,
                                                    @Param("excludedStatuses") java.util.Collection<LoanProductStatus> excludedStatuses,
                                                    @Param("search") String search,
                                                    Pageable pageable);

    Optional<LoanProductSetting> findByIdAndSaccoIdAndActiveTrue(UUID id, String saccoId);

    List<LoanProductSetting> findBySaccoIdAndActiveTrue(String saccoId);

    List<LoanProductSetting> findBySaccoIdOrderByLoanTypeAsc(String saccoId);

    List<LoanProductSetting> findBySaccoIdAndProductStatusNot(String saccoId, com.sacco.mvp.domain.LoanProductStatus productStatus);

    boolean existsBySaccoId(String saccoId);

    boolean existsBySaccoIdAndLoanType(String saccoId, LoanType loanType);

    boolean existsBySaccoIdAndProductCodeIgnoreCase(String saccoId, String productCode);

    boolean existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot(String saccoId, String productCode, UUID id);
}
