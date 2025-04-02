import asyncio
import socket
from pathlib import Path
from typing import cast

import pexpect.socket_pexpect
from qemu.qmp import QMPClient


def _qemu_bool(value: bool | None) -> str | None:
    if value is None:
        return None
    return "on" if value else "off"


def _check_spaces(value: str) -> str:
    if " " in value:
        raise ValueError("Spaces are not allowed in QEMU arguments.")
    return value


def _convert_args(args: dict[str, str | int | bool | None]) -> dict[str, str]:
    return {k: _qemu_bool(v) if isinstance(v, bool) else _check_spaces(str(v)) for k, v in args.items()}


class AsyncToSyncWithLoop:
    def __init__(self, func):
        self.func = func
        self.method = None
        self.bound_instance = None

    def __get__(self, instance, owner):
        self.method = self.func.__get__(instance, owner)
        self.bound_instance = instance

        if not hasattr(instance, "_async_to_sync_with_loop__loop"):
            instance._async_to_sync_with_loop__loop = asyncio.new_event_loop()

        return self

    def __call__(self, *args, **kwargs):
        if self.method is None:
            raise RuntimeError("AsyncToSync method is not bound to an instance.")
        # noinspection PyProtectedMember
        return self.bound_instance._async_to_sync_with_loop__loop.run_until_complete(self.method(*args, **kwargs))


def async_to_sync_with_loop(func):
    return AsyncToSyncWithLoop(func)


class QEMUController:
    """
    A class to control QEMU using QMP and monitor sockets.

    We use both the QMP and the monitor sockets since while QMP is generally more powerful, it's quite broken and some
    commands are not implemented. The monitor socket is a bit more stable, but more complex to use since it's meant to
    be used interactively.
    """

    def __init__(self, qmp_path: str, monitor_path: str):
        self._monitor = None
        self.qmp_path = qmp_path
        self.monitor_path = monitor_path
        self._monitor_sock = None
        self._open = False

    def open(self):
        if self._open:
            raise RuntimeError("QEMUController is already open.")
        self._open = True

        self._qmp_setup()

        self._monitor_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._monitor_sock.connect(self.monitor_path)
        self._monitor_sock.settimeout(3)

        self._monitor = pexpect.socket_pexpect.SocketSpawn(self._monitor_sock, timeout=1)

        try:
            self._monitor.expect(r"QEMU (?:\d.?)+ monitor - type 'help' for more information")
        except pexpect.TIMEOUT as e:
            raise RuntimeError(
                "Failed to connect to QEMU monitor; are there any other connections open to the socket?"
            ) from e
        self._monitor_expect_prompt()

    def close(self):
        if not self._open:
            raise RuntimeError("QEMUController is not open.")
        self._qmp_teardown()
        self._monitor_sock.close()
        self._open = False

    def __enter__(self):
        self.open()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def _monitor_expect_prompt(self):
        self._monitor.expect(r"\(qemu\) ", timeout=1)

    def _monitor_send_command(self, command: str):
        print("Sending monitor command:", command.strip())
        self._monitor.send(command)

    @async_to_sync_with_loop
    async def _qmp_setup(self):
        self._qmp = QMPClient("etchdroid-test-runner")
        await self._qmp.connect(self.qmp_path)

    @async_to_sync_with_loop
    async def _qmp_teardown(self):
        if not self._open:
            raise RuntimeError("QEMUController is not open.")
        await self._qmp.disconnect()

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
                "if": iface,
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
        command = f"drive_add {':'.join(slot_params)} {','.join((f'{k}={v}' for k, v in args.items()))}\n"

        self._monitor_send_command(command)
        try:
            self._monitor.expect(r"OK")
        except pexpect.TIMEOUT as e:
            raise RuntimeError(f"Failed to add drive: {self._monitor.before}") from e
        self._monitor_expect_prompt()

    # noinspection PyShadowingBuiltins
    @async_to_sync_with_loop
    async def device_add(
        self,
        driver: str,
        *,
        bus: str | None = None,
        id: str | None = None,
        **kwargs: str | int | bool | None,
    ) -> object:
        args = _convert_args(
            {
                "bus": bus,
                "id": id,
                **kwargs,
            }
        )
        command = f"device_add {driver},{','.join((f'{k}={v}' for k, v in args.items()))}\n"

        self._monitor_send_command(command)
        index = self._monitor.expect([r"\(qemu\) ", r"Error:"])
        if index == 1:
            raise RuntimeError(f"Failed to add device: {self._monitor.after}")

    # noinspection PyShadowingBuiltins
    @async_to_sync_with_loop
    async def get_block_device(self, id: str) -> dict:
        block_info = cast(list[dict], await self._qmp.execute("query-block"))
        for block in block_info:
            if block["device"] == id:
                return block
        else:
            raise ValueError(f"Block device {id} not found")

    @async_to_sync_with_loop
    async def _sleep(self, delay: float):
        await asyncio.sleep(delay)

    # noinspection PyShadowingBuiltins
    def device_del(self, id: str) -> object:
        command = f"device_del {id}\n"
        self._monitor_send_command(command)
        index = self._monitor.expect([r"\(qemu\) ", r"Error:"])
        if index == 1:
            raise RuntimeError(f"Failed to delete device: {self._monitor.after}")

    # noinspection PyShadowingBuiltins
    def add_usb_drive(
        self,
        id: str,
        *,
        file: str | Path,
        bus: str,
        format: str = "raw",
    ):
        self.drive_add(
            id=id,
            iface="none",
            file=file,
            format=format,
        )
        self._sleep(0.5)
        self.device_add(
            "usb-storage",
            id=id,
            bus=bus,
            drive=id,
            removable=True,
        )
