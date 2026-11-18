import { describe, expect, it } from "vitest";

import { clearCsrfCookie, setCsrfCookie } from "../test/mocks";
import { CSRF_COOKIE, isMutating, parseCookie, readCsrfToken } from "./csrf";

describe("parseCookie", () => {
  it("hittar rätt cookie bland flera", () => {
    expect(parseCookie(`a=1; ${CSRF_COOKIE}=token; b=2`, CSRF_COOKIE)).toBe("token");
  });

  it("ger undefined när cookien saknas eller strängen är tom", () => {
    expect(parseCookie("a=1; b=2", CSRF_COOKIE)).toBeUndefined();
    expect(parseCookie("", CSRF_COOKIE)).toBeUndefined();
  });

  it("matchar hela namnet, inte en ände av ett annat namn", () => {
    expect(parseCookie(`x${CSRF_COOKIE}=fel`, CSRF_COOKIE)).toBeUndefined();
  });

  it("avkodar procentkodade värden och behåller ett värde som inte går att avkoda", () => {
    expect(parseCookie(`${CSRF_COOKIE}=a%2Fb%3D`, CSRF_COOKIE)).toBe("a/b=");
    expect(parseCookie(`${CSRF_COOKIE}=%E0%A4%A`, CSRF_COOKIE)).toBe("%E0%A4%A");
  });

  it("behåller likhetstecken i värdet", () => {
    expect(parseCookie(`${CSRF_COOKIE}=abc==`, CSRF_COOKIE)).toBe("abc==");
  });
});

describe("readCsrfToken", () => {
  it("läser token ur den riktiga cookien, vid varje anrop", () => {
    expect(readCsrfToken()).toBeUndefined();
    setCsrfCookie("första");
    expect(readCsrfToken()).toBe("första");
    setCsrfCookie("andra");
    expect(readCsrfToken()).toBe("andra");
    clearCsrfCookie();
    expect(readCsrfToken()).toBeUndefined();
  });
});

describe("isMutating", () => {
  it.each(["POST", "PUT", "PATCH", "DELETE", "post"])("%s ändrar data", (method) => {
    expect(isMutating(method)).toBe(true);
  });

  it.each(["GET", "HEAD", "OPTIONS", "get"])("%s ändrar inte data", (method) => {
    expect(isMutating(method)).toBe(false);
  });
});
