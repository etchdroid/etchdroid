import socket

from asgiref.sync import async_to_sync
from qemu.qmp import QMPClient


def _qemu_bool(value: bool | None) -> str | None:
    if value is None:
        return None
    return "on" if value else "off"


def _check_spaces(value: str) -> str:
    if " " in value:
        raise ValueError("Spaces are not allowed in QEMU arguments.")
    return value


class QEMUController:
    def __init__(self, qmp_path: str, monitor_path: str):
        self.qmp_path = qmp_path
        self.monitor_path = monitor_path
        self._open = False

    def __enter__(self):
        if self._open:
            raise RuntimeError("QEMUController is already open.")
        self._open = True

        self._qmp_setup()

        self.monitor = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.monitor.connect(self.monitor_path)
        self.monitor.settimeout(1)
        return self

    @async_to_sync
    async def _qmp_setup(self):
        self.qmp = QMPClient("etchdroid-test-runner")
        await self.qmp.connect(self.qmp_path)

    def __exit__(self, exc_type, exc_val, exc_tb):
        if not self._open:
            raise RuntimeError("QEMUController is not open.")
        self._qmp_teardown()
        self.monitor.close()

    @async_to_sync
    async def _qmp_teardown(self):
        if not self._open:
            raise RuntimeError("QEMUController is not open.")
        await self.qmp.disconnect()

    # noinspection PyShadowingBuiltins
    def drive_add(
        self,
        *,
        slot: int = 0,
        domain: int | None = None,
        bus_id: int | None = None,
        id: str | None = None,
        file: str | None = None,
        bus: str | None = None,
        iface: str | None = None,
        unit: str | None = None,
        media: str | None = None,
        index: int | None = None,
        snapshot: bool | None = None,
        format: str | None = None,
        cache: bool | None = None,
        readonly: bool | None = None,
        copy_on_read: bool | None = None,
    ):
        args = {
            k: _check_spaces(str(v))
            for k, v in {
                "id": id,
                "file": file,
                "bus": bus,
                "iface": iface,
                "unit": unit,
                "media": media,
                "index": index,
                "snapshot": _qemu_bool(snapshot),
                "format": format,
                "cache": _qemu_bool(cache),
                "readonly": _qemu_bool(readonly),
                "copy_on_read": _qemu_bool(copy_on_read),
            }.items()
            if v is not None
        }
        slot_params = [str(i) for i in [domain, bus_id, slot] if i is not None]
        command = f"drive_add {':'.join(slot_params)} {','.join((f'{k}={v}' for k, v in args.items()))}"

        self.monitor.send(command.encode())
        resp = self.monitor.recv(4096)

        if b"OK" not in resp:
            raise RuntimeError(f"Failed to add drive: {resp.decode()}")

    # noinspection PyShadowingBuiltins
    def drive_del(self, id: str):
        command = f"drive_del {id}"
        self.monitor.send(command.encode())
        resp = self.monitor.recv(4096)

        if b"OK" not in resp:
            raise RuntimeError(f"Failed to delete drive: {resp.decode()}")

    # noinspection PyShadowingBuiltins
    @async_to_sync
    async def device_add(
        self,
        driver: str,
        *,
        bus: str | None = None,
        id: str | None = None,
        **kwargs: str | int | bool | None,
    ) -> object:
        return await self.qmp.execute(
            "device_add",
            arguments={
                "driver": driver,
                "bus": bus,
                "id": id,
                **kwargs,
            },
        )

    # noinspection PyShadowingBuiltins
    @async_to_sync
    async def device_del(self, id: str) -> object:
        return await self.qmp.execute(
            "device_del",
            arguments={
                "id": id,
            },
        )

    # noinspection PyShadowingBuiltins
    def add_usb_drive(
        self,
        id: str,
        *,
        file: str,
        bus: str,
        format: str = "raw",
    ):
        self.drive_add(
            id=f"{id}-drive",
            iface="none",
            file=file,
            format=format,
        )
        self.device_add(
            "usb-storage",
            id=id,
            bus=bus,
            drive=f"{id}-drive",
        )
