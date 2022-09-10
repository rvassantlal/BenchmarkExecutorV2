## Usage
1) Implement interfaces `controller.IBenchmarkStrategy`, `worker.ISetupWorker`, and `worker.IWorkerEventProcessor`;
2) Configure the `benchmark.config` file;
3) Start controller by executing the following command:
    ```
    java -cp "*" controller.BenchmarkControllerStartup benchmark.config
    ```
4) Start workers by executing the following command:
    ```
    java -cp "*" worker.WorkerStartup <controller's ip> <controller's port>
    ```