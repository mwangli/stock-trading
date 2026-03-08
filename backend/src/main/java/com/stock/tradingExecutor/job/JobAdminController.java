package com.stock.tradingExecutor.job;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobAdminController {

    private final JobSchedulerService jobSchedulerService;
    private final JobConfigRepository jobConfigRepository;

    @GetMapping
    public ResponseEntity<List<JobConfig>> listJobs() {
        return ResponseEntity.ok(jobConfigRepository.findAll());
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Void> runJob(@PathVariable Long id) {
        jobSchedulerService.runJobNow(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> payload) {
        boolean active = payload.get("active");
        jobSchedulerService.toggleJobStatus(id, active);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cron")
    public ResponseEntity<Void> updateCron(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String cron = payload.get("cron");
        jobSchedulerService.updateJobCron(id, cron);
        return ResponseEntity.ok().build();
    }
}
