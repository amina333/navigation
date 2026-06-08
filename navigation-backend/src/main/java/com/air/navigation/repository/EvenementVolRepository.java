package com.air.navigation.repository;

import com.air.navigation.entities.EvenementVol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvenementVolRepository extends JpaRepository<EvenementVol, Long> {

    List<EvenementVol> findByFlightIdOrderByDateHeureAsc(Long flightId);
}
