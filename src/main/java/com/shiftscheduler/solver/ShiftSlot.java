package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

// One slot that needs a person. A shift needing 3 workers makes 3 of these.
//
// Timefold can run on JPA entities directly, see:
// https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/jpa-jaxb-json-integration
// That route needs a score converter, deep-clone annotations, and every lazy
// relation initialized before solving starts.
// I mapped to plain classes instead so nothing Hibernate manages is touched during the 40 seconds
// search, which runs outside a transaction.


@PlanningEntity
public class ShiftSlot {

    @PlanningId
    private Long id;

    private PlanningShift shift;
    private Long jobPositionId;
    private String jobPositionName;


    // Whether the shift can run without this position.
    private boolean essential;

    // Filled by the manager before solving. The solver still counts it in
    // every rule but can't move it.
    @PlanningPin
    private boolean pinned;

    // The only field the solver changes.
    // allowsUnassigned lets it leave a slot empty instead of failing when
    // there aren't enough people. Empty slots are penalised, not forbidden.
    @PlanningVariable(allowsUnassigned = true)
    private PlanningEmployee employee;

    public ShiftSlot() {
    }

    public ShiftSlot(Long id, PlanningShift shift, Long jobPositionId, String jobPositionName, boolean essential) {
        this.id = id;
        this.shift = shift;
        this.jobPositionId = jobPositionId;
        this.jobPositionName = jobPositionName;
        this.essential = essential;
    }

    public Long getId() {
        return id;
    }

    public PlanningShift getShift() {
        return shift;
    }

    public Long getJobPositionId() {
        return jobPositionId;
    }

    public String getJobPositionName() {
        return jobPositionName;
    }

    public boolean isEssential() {
        return essential;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public PlanningEmployee getEmployee() {
        return employee;
    }

    public void setEmployee(PlanningEmployee employee) {
        this.employee = employee;
    }

    @Override
    public String toString() {
        return jobPositionName + " @ " + shift;
    }
}