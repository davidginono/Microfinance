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
    Optional<LoanProductSetting> findBySaccoIdAndCustomRecordingConfigurationTrue(String saccoId);
    @Query("select p from LoanProductSetting p where p.saccoId=:saccoId and p.loanType=:loanType and p.active=true and p.customRecordingConfiguration=false")
    Optional<LoanProductSetting> findBySaccoIdAndLoanTypeAndActiveTrue(@Param("saccoId") String saccoId, @Param("loanType") LoanType loanType);

    @Query("select p from LoanProductSetting p where p.saccoId=:saccoId and p.loanType=:loanType and p.customRecordingConfiguration=false")
    Optional<LoanProductSetting> findBySaccoIdAndLoanType(@Param("saccoId") String saccoId, @Param("loanType") LoanType loanType);

    Optional<LoanProductSetting> findByIdAndSaccoId(UUID id, String saccoId);

    List<LoanProductSetting> findBySaccoIdAndIdIn(String saccoId, java.util.Collection<UUID> ids);

    @Query(value = """
        select p.* from loan_product_settings p
        where p.sacco_id = :saccoId
          and p.custom_recording_configuration = false
          and (p.product_status is null or p.product_status not in :excludedStatuses)
          and (:search = ''
            or lower(coalesce(p.product_code, '')) like concat(:search, '%')
            or lower(coalesce(p.product_name, '')) like concat(:search, '%'))
        order by coalesce(p.display_order, 2147483647), lower(coalesce(p.product_name, '')), p.id
        """, nativeQuery = true, queryRewriter = com.sacco.mvp.support.ResourceTableSort.ProductConfiguration.class)
    Page<LoanProductSetting> findConfigurationPage(@Param("saccoId") String saccoId,
                                                    @Param("excludedStatuses") java.util.Collection<String> excludedStatuses,
                                                    @Param("search") String search,
                                                    Pageable pageable);

    Optional<LoanProductSetting> findByIdAndSaccoIdAndActiveTrue(UUID id, String saccoId);

    @Query("select p from LoanProductSetting p where p.saccoId=:saccoId and p.active=true and p.customRecordingConfiguration=false")
    List<LoanProductSetting> findBySaccoIdAndActiveTrue(@Param("saccoId") String saccoId);

    @Query("select p from LoanProductSetting p where p.saccoId=:saccoId and p.customRecordingConfiguration=false order by p.loanType")
    List<LoanProductSetting> findBySaccoIdOrderByLoanTypeAsc(@Param("saccoId") String saccoId);

    @Query("select p from LoanProductSetting p where p.saccoId=:saccoId and p.productStatus<>:productStatus and p.customRecordingConfiguration=false")
    List<LoanProductSetting> findBySaccoIdAndProductStatusNot(@Param("saccoId") String saccoId, @Param("productStatus") com.sacco.mvp.domain.LoanProductStatus productStatus);

    boolean existsBySaccoId(String saccoId);

    boolean existsBySaccoIdAndLoanType(String saccoId, LoanType loanType);

    boolean existsBySaccoIdAndProductCodeIgnoreCase(String saccoId, String productCode);

    boolean existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot(String saccoId, String productCode, UUID id);
}
