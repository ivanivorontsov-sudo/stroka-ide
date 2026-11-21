import builtins
import ctypes
import io
import os
import sys
import threading
import traceback

_state = {"tid": None}


class _Out(io.TextIOBase):
    def __init__(self, cb, err):
        self._cb = cb
        self._err = err

    def writable(self):
        return True

    def write(self, s):
        if s:
            self._cb.out(str(s), self._err)
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return False


class _In(io.TextIOBase):
    def __init__(self, cb):
        self._cb = cb

    def readable(self):
        return True

    def readline(self, size=-1):
        r = self._cb.readLine("")
        if r is None:
            return ""
        return str(r) + "\n"

    def read(self, size=-1):
        return self.readline()


def run(code, filename, cwd, cb):
    _state["tid"] = threading.get_ident()
    saved = (sys.stdout, sys.stderr, sys.stdin, builtins.input, os.getcwd(), list(sys.path), list(sys.argv))
    out = _Out(cb, False)
    err = _Out(cb, True)

    def _input(prompt=""):
        if prompt:
            out.write(str(prompt))
        r = cb.readLine(str(prompt))
        if r is None:
            raise EOFError("ввод закрыт")
        return str(r)

    sys.stdout = out
    sys.stderr = err
    sys.stdin = _In(cb)
    builtins.input = _input
    status = 0
    try:
        if cwd:
            os.chdir(cwd)
            sys.path.insert(0, cwd)
        sys.argv = [filename]
        g = {"__name__": "__main__", "__file__": filename, "__builtins__": builtins}
        exec(compile(code, filename, "exec"), g)
    except SystemExit as e:
        if e.code is None:
            status = 0
        elif isinstance(e.code, int):
            status = e.code
        else:
            err.write(str(e.code) + "\n")
            status = 1
    except KeyboardInterrupt:
        err.write("\nОстановлено\n")
        status = 130
    except BaseException:
        et, ev, tb = sys.exc_info()
        if tb is not None and tb.tb_next is not None:
            tb = tb.tb_next
        err.write("".join(traceback.format_exception(et, ev, tb)))
        status = 1
    finally:
        try:
            out.flush()
        except Exception:
            pass
        sys.stdout, sys.stderr, sys.stdin, builtins.input = saved[0], saved[1], saved[2], saved[3]
        try:
            os.chdir(saved[4])
        except Exception:
            pass
        sys.path[:] = saved[5]
        sys.argv = saved[6]
        _state["tid"] = None
    return status


def stop():
    tid = _state["tid"]
    if tid is None:
        return False
    n = ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(tid), ctypes.py_object(KeyboardInterrupt))
    return n == 1


def version():
    return sys.version.split()[0]
