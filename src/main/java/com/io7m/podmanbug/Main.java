/*
 * Copyright © 2024 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.podmanbug;

import org.postgresql.PGProperty;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

public final class Main
{
  private Main()
  {

  }

  public static void main(
    final String[] args)
    throws Exception
  {
    /*
     * Kill any old test containers that might be hanging around.
     */

    {
      final var command = new ArrayList<String>();
      command.add("podman");
      command.add("rm");
      command.add("-f");
      command.add("podmanbug");

      new ProcessBuilder(command)
        .inheritIO()
        .start()
        .waitFor();
    }

    /*
     * Run a postgres container that binds to ::
     */

    final var command = new ArrayList<String>();
    command.add("podman");
    command.add("run");
    command.add("--interactive");
    command.add("--tty");
    command.add("--env");
    command.add("POSTGRES_DB=db-xyz");
    command.add("--env");
    command.add("POSTGRES_PASSWORD=db-password");
    command.add("--env");
    command.add("POSTGRES_USER=db-user");
    command.add("--publish");
    command.add("[::]:5432:5432/tcp");
    command.add("--name");
    command.add("podmanbug");
    command.add("docker.io/postgres:15.6-alpine3.19");

    final var process =
      new ProcessBuilder(command)
        .inheritIO()
        .start();

    /*
     * Start a background thread that tries to `nc` to the postgres socket
     * every time the user presses return.
     */

    Thread.startVirtualThread(() -> {
      while (true) {
        try {
          final var c = System.in.read();
          final var nc = new ArrayList<String>();
          nc.add("nc");
          nc.add("-vvv");
          nc.add("-z");
          nc.add("::");
          nc.add("5432");
          new ProcessBuilder(nc)
            .inheritIO()
            .start()
            .waitFor();
        } catch (final Exception e) {
          // Don't care
        }
      }
    });

    /*
     * Keep repeatedly trying to open a JDBC connection to the server.
     */

    final var properties = new Properties();
    properties.setProperty(PGProperty.USER.getName(), "db-user");
    properties.setProperty(PGProperty.PASSWORD.getName(), "db-password");
    properties.setProperty(PGProperty.PG_HOST.getName(), "[::]");
    properties.setProperty(PGProperty.PG_PORT.getName(), "5432");
    properties.setProperty(PGProperty.PG_DBNAME.getName(), "db-xyz");

    while (true) {
      try (var conn = DriverManager.getConnection(
        "jdbc:postgresql://",
        properties)) {
        if (conn.isValid(1000)) {
          break;
        }
      } catch (final SQLException e) {
        System.err.printf("Couldn't connect: %s%n".formatted(e.getMessage()));
        Thread.sleep(1_000L);
      }
    }
  }
}
