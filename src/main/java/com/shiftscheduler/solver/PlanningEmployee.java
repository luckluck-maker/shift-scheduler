package com.shiftscheduler.solver;


import ai.timefold.solver.core.api.domain.common.PlanningId;

// A copy of Employee with only the fields the rules use.
// Hours are kept in minutes so shift lengths add up without rounding.
public class PlanningEmployee {

    // Lets the solver match objects across its internal copies.
    @PlanningId
    private Long id;

    private String fullName;
    private Long jobPositionId;

    // How many shifts this employee can work this week — the contract converted
    // to shifts, less any days they are on leave.
    private int availableShifts;
    private int minimumShifts;

    public PlanningEmployee() {
    }

    public PlanningEmployee(Long id, String fullName, Long jobPositionId,
                            int availableShifts, int minimumShifts) {
        this.id = id;
        this.fullName = fullName;
        this.jobPositionId = jobPositionId;
        this.availableShifts = availableShifts;
        this.minimumShifts =  minimumShifts;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Long getJobPositionId() {
        return jobPositionId;
    }

    public int getAvailableShifts() {
        return availableShifts;
    }
    @Override
    public String toString() {
        return fullName;
    }

    public int getMinimumShifts() {
        return minimumShifts;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlanningEmployee e && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}