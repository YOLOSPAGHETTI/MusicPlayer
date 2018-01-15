package com.example.android.uamp.model;

/**
 * Created by Kyler C on 11/29/2017.
 */

// Calculates the percentage of completion for the loading processes to show in the progress bar
public class CompletionCalculation {
    private int processes;
    public int completionPerc;

    public CompletionCalculation(int processes) {
        this.processes = processes;
        completionPerc = 0;
    }

    public void recalculate(int currentProcess, int processCompletionPerc) {
        float sliver = (float)100/(float)processes;
        completionPerc = Math.round(sliver*currentProcess+processCompletionPerc*(sliver/(float)100));
    }
}
