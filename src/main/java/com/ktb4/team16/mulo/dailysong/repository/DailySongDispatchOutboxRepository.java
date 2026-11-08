package com.ktb4.team16.mulo.dailysong.repository;

import com.ktb4.team16.mulo.dailysong.entity.DailySongDispatchOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySongDispatchOutboxRepository extends JpaRepository<DailySongDispatchOutbox, String> {
}
