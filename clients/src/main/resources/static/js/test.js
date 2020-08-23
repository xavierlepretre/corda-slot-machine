"use strict";

// This is a bare-bones version of the logic found in slots.js

$(window).on("load", function () {
  function setMessage(message) {
    if (typeof message === "object")
      message = `object: ${JSON.stringify(message)}`;
    $("#message").html("Result: " + message);
  }

  function makeRequest(params, url, type, dataType = "text") {
    // https://api.jquery.com/jQuery.ajax/
    $.ajax({
      url: url,
      type: type,
      data: params,
      dataType: dataType,
      timeout: 10000,
      success: function (data) {
        setMessage(data);
        return false;
      },
      error: function (jqXHR, textStatus, errorThrown) {
        const msg =
          textStatus && errorThrown
            ? `${textStatus} -- ${errorThrown}`
            : textStatus
            ? textStatus
            : errorThrown
            ? "" + errorThrown
            : "Error?!";
        setMessage(msg);
        return false;
      },
    });
  }

  function uuidv4() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (
      c
    ) {
      var r = (Math.random() * 16) | 0,
        v = c == "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  const accountNameParams = { name: uuidv4() };

  $("#test").click(function () {
    makeRequest(null, "/test", "GET");
  });
  $("#echo").click(function () {
    makeRequest({ payload: "OK" }, "/echo", "GET");
  });
  $("#create").click(function () {
    makeRequest(accountNameParams, "/create", "POST");
  });
  $("#balance").click(function () {
    makeRequest(accountNameParams, "/balance", "GET");
  });
  $("#payout").click(function () {
    makeRequest(accountNameParams, "/payout", "POST");
  });
  $("#spin").click(function () {
    makeRequest(accountNameParams, "/spin", "POST", "json");
  });
  $("#reels").click(function () {
    function getReels(payout) {
      try {
        const result = { payout_credits: payout };
        return window.ioServer.getResultReels(result).reels;
      } catch (err) {
        return err;
      }
    }
    const message = "200 50 20 15 15 15 15 15 13 12 10 4 4 4 4 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
      .split(" ")
      .map((payout) => `${payout}: ${getReels(payout)}`)
      .join("\n");
    setMessage(message);
  });
});
