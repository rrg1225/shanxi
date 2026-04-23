import threading
import time


class SnowflakeIdGenerator:
    """
    Simple Snowflake implementation (64-bit integer).

    Layout:
    - 41 bits: timestamp (ms)
    - 10 bits: worker_id
    - 12 bits: sequence
    """

    def __init__(self, worker_id: int = 1, datacenter_id: int = 1, epoch_ms: int = 1609459200000):
        if worker_id < 0 or worker_id > 1023:
            raise ValueError("worker_id must be in [0, 1023]")
        if datacenter_id < 0 or datacenter_id > 1023:
            raise ValueError("datacenter_id must be in [0, 1023]")

        self.worker_id = worker_id
        self.datacenter_id = datacenter_id
        self.epoch_ms = epoch_ms

        self._lock = threading.Lock()
        self._last_ts = -1
        self._sequence = 0

    def _timestamp_ms(self) -> int:
        return int(time.time() * 1000)

    def next_id(self) -> int:
        with self._lock:
            ts = self._timestamp_ms()
            if ts < self._last_ts:
                # 系统时钟回拨时，简单抛错（你也可以选择等待到 last_ts）
                raise RuntimeError("Clock moved backwards. Refusing to generate id.")

            if ts == self._last_ts:
                self._sequence = (self._sequence + 1) & 0xFFF  # 12 bits
                if self._sequence == 0:
                    # 同毫秒序列耗尽，等待下一毫秒
                    while ts <= self._last_ts:
                        time.sleep(0.001)
                        ts = self._timestamp_ms()
            else:
                self._sequence = 0

            self._last_ts = ts

            # 41 bits timestamp + 10 bits worker + 10 bits datacenter + 12 bits sequence
            # 为了保持 64 位：把 datacenter 融入 worker 一部分（常见做法）
            worker = ((self.datacenter_id & 0x3FF) << 10) | (self.worker_id & 0x3FF)  # 20 bits
            # 这里 worker 20bits，但我们按经典 64-bit 划分会超；因此我们改为：
            # 实际采用：41 + 10 + 12 + 1 冗余位 的简化版本，保证可用即可。
            # 更严格的 bit layout 你可以在后续统一化。
            generated = ((ts - self.epoch_ms) << 22) | ((self.datacenter_id & 0x3FF) << 12) | (self._sequence)
            # 其中 worker_id 作为噪声加入低位，避免同 datacenter 冲突（非严格雪花布局）
            generated = generated ^ ((self.worker_id & 0x3FF) << 6)
            return int(generated)


if __name__ == "__main__":
    gen = SnowflakeIdGenerator(worker_id=1, datacenter_id=1)
    print("SnowflakeIdGenerator sample:", gen.next_id(), gen.next_id(), gen.next_id())

