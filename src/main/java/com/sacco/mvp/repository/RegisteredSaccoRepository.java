package com.sacco.mvp.repository;

import com.sacco.mvp.domain.RegisteredSacco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RegisteredSaccoRepository extends JpaRepository<RegisteredSacco, String> {
    List<RegisteredSacco> findByActiveTrueOrderBySaccoNameAsc();

    List<RegisteredSacco> findBySaccoIdIn(Collection<String> saccoIds);

    interface LoanTopUpRow {
        String getSaccoId();
        String getSaccoName();
        boolean isLoanTopUpEnabled();
    }

    @Query("""
        select r.saccoId as saccoId, r.saccoName as saccoName,
               coalesce(s.loanTopUpEnabled, true) as loanTopUpEnabled
        from RegisteredSacco r left join SaccoSettings s on s.saccoId = r.saccoId
        where r.active = true and upper(r.saccoId) <> 'PLATFORM'
          and (:id is null or r.saccoId = :id)
          and (:q = '' or lower(r.saccoName) like concat('%', :q, '%') or lower(r.saccoId) like concat('%', :q, '%'))
        order by lower(r.saccoName), r.saccoId
        """)
    Page<LoanTopUpRow> loanTopUpAccess(@Param("id") String saccoId, @Param("q") String query, Pageable pageable);
}
