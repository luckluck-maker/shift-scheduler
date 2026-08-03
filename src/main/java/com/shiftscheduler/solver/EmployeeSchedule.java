package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

// Everything the solver needs for one week. This is the object it copies
// over and over while searching.
@PlanningSolution
public class EmployeeSchedule {

    private Long scheduleId;

    // ValueRangeProvider = the pool to pick from when filling a slot.
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<PlanningEmployee> employees;

    @ProblemFactCollectionProperty
    private List<UnavailableDay> unavailableDays;

    @ProblemFactCollectionProperty
    private List<ShiftDislike> dislikes;

    @ProblemFactCollectionProperty
    private List<PriorShiftEnd> priorShiftEnds;

    // The only thing that changes during solving.
    @PlanningEntityCollectionProperty
    private List<ShiftSlot> slots;

    // Three levels. A higher one always wins, whatever the numbers below it.
    //   hard   - leave, CANNOT, rest, one per day, weekly limits
    //   medium - a slot nobody was assigned to
    //   soft   - PREFERS_NOT, fairness
    // Understaffing sits in medium so it can't be traded against preferences.
    // In soft it would come down to weights, and enough small preference
    // penalties would outweigh leaving a shift short.
    @PlanningScore
    private HardMediumSoftScore score;

    public EmployeeSchedule() {
    }

    public EmployeeSchedule(Long scheduleId,
                            List<PlanningEmployee> employees,
                            List<UnavailableDay> unavailableDays,
                            List<ShiftDislike> dislikes,
                            List<PriorShiftEnd> priorShiftEnds,
                            List<ShiftSlot> slots) {
        this.scheduleId = scheduleId;
        this.employees = employees;
        this.unavailableDays = unavailableDays;
        this.dislikes = dislikes;
        this.priorShiftEnds = priorShiftEnds;
        this.slots = slots;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public List<PlanningEmployee> getEmployees() {
        return employees;
    }

    public List<UnavailableDay> getUnavailableDays() {
        return unavailableDays;
    }

    public List<ShiftDislike> getDislikes() {
        return dislikes;
    }

    public List<PriorShiftEnd> getPriorShiftEnds() {
        return priorShiftEnds;
    }

    public List<ShiftSlot> getSlots() {
        return slots;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}