package com.sacco.mvp.repository;

import com.sacco.mvp.domain.RegisteredSacco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegisteredSaccoRepository extends JpaRepository<RegisteredSacco, String> {
    List<RegisteredSacco> findByActiveTrueOrderBySaccoNameAsc();
}
