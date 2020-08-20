<!DOCTYPE html>
<html>
<title>Slots Documentation</title>

<xmp theme="cerulean" style="display:none;">

# Installation

1. Unzip the `slots_vX.X.X.zip` zip file you downloaded.
1. Edit file `slots/includes/config.php` to set up the database connection information.
    - `config.php` has a number of constants to configure the DB connection.
    - The first 4 are the most important ones, they define how to connect to your MySQL database.
    - Modify each of these values to match your database connection configuration. If you do not
      have this information, your website host should be able to provide you with it.
    - The rest of the values will be explained in the relevant sections below.
      You can ignore them for now.
1. Connect to your MySQL database, and execute the contents of the `.sql` files in the `database` directory that's on your local computer.
    - The easiest way to do this is to use something like PHPMyAdmin which you can probably find on your web hosting management website.
      If you require help accessing your database, your website host should be able to point you in the right direction.
    - Make sure to execute these in order.
    - **IMPORTANT!: Do not upload the `.sql` files to your server. This can lead to security problems!**
1. Create a sub-directory on your website server called `slots` where you want the game to live.
1. Upload all the files of the `slots` directory on your local computer to the `slots` directory on the server
1. Navigate to `your_website.com/slots`, you should see the game on your site, and be able to play it.
    If you can, then your game is working! Look at the following steps to set it up for your needs and integrate it properly into your site.
    If you cannot see the game, or cannot play it, refer to the troubleshooting section for help with the most common issues.
1. Now your game is working!
1. Decide whether you want Slots to run on their own page, or integrated in one of your site's existing pages, by reading the
    two sub-sections below.
1. By default, Slots creates anonymous users that have a "balance" of 100, and can play essentially for free for quite a while.
    If your site will require users to sign up / login to play, read "Integration with user management / money management systems" below.
1. Set up your game types, and which of those get displayed on your site. **Make sure to follow instructions in "Setting up Game Types and Prizes" below**,
    particularly related to disabling the game types you won't use. **You may leave your system open to exploitation by cheaters if you don't
    delete unused game types!**
1. Finally, take a look at the "Look and feel" section for help on customizing the looks of your game.

## Having Slots in their own page

At this point, you have a page with Slots running. You can simply modify `slots/index.php` with the HTML / CSS
that you need for the page around the game, and then point to this page from somewhere else in your site.

You probably want to leave only one of the several repeated blocks of 3 lines that include all the different games types.
Read more about this on "Picking a game type" below.

## Installing Slots as part of an existing page in your site

If you have an existing page on your site that you want to add Slots to, there are 2 ways:

1. Actually add the Slots HTML to the page required:
    1. At the top of your page, before any output is rendered, include the PHP code that's at the top
        of `slots/index.php`.
    1. In the `<head>` of your page, include all the CSS stylesheets from `slots/index.php`

    1. In the place inside your page where the game should be rendered, define which game type you want, e.g.:
        `$gameType = 'default';`
    1. Then include the Slots template file: `include('includes/slots_template.php');`

    1. At the bottom of your page, copy all the code that's in all the `<script>` tags in `slots/index.php`.
        - Important! If you already have jQuery in your page (this is highly likely), do not copy the line that
            includes jQuery, you already have included it. Also make sure that the inclusion of `slots.js`
            happens *after* the inclusion of jQuery.
        - If you see jQuery-ui among the `<script>` tags, you will need it. If your site currently uses jQuery but not jQuery UI,
            you will need to add a version of jQuery UI that is compatible with your existing version of  jQuery.
        - If this would result in incompatibilities, see the IFrame method below instead of adding the slots directly into your
            existing page.
    1. For all of these steps, you may need to modify the relevant paths to point to the correct files.
1. Another option is to add an IFrame on the page you want to render Slots on, pointing to `slots/index.php`.
    This may be easier to do and take less work, but it'll have implications if your page has components that the
    game needs to interact with. For example, if your users have a balance on your site, and you have a
    menu bar or something similar displaying that balance at all times, you will want the game to keep
    that up to date, which they are prepared to do, but it won't work with an IFrame.

If you or your developer are having trouble deciding which approach is best, feel free to contact us
at [info@slotmachinescript.com](mailto:info@slotmachinescript.com) and we'll give you a hand.



# Integration with user management / money management systems

By default, Slots allow users to play anonymously, for ease of installation / testing, but you may want
to change this based on your needs.

## Anonymous Playing / FreePlay

In anonymous mode, when a user views the game, a record is created for them in the `luckscript_users`
table, and a cookie is set for them. This anonymous user is created with a starting balance of 100, which you can
modify by editing the `FREEPLAY_CREDITS` constant near the top of file `slots/includes/login_freeplay.php`.

When this user returns to the page, they will be identified by their cookie. If they don't have the cookie, the game
might still find them by their IP address and User Agent (the way their browser identifies itself). In either case,
they continue to have their original balance and to keep playing.

However, the game doesn't know anything about these users, such as their name or e-mail address, and the way they
are identified is very insecure. It's just a low friction way of letting everyone play.

You might want to use this mode in several different use cases:

- If you are at a trade show, or a situation where people play in person, there's no need
  to authenticate your users in order to give them prizes.
- If you have a marketing or lead capture website where people can play for free, and they get
  redirected to some other page when they win, you may be able to use FreePlay.
- This is also useful for "fun" use cases, like when simply trying to engage your audience,
  or using the game for other purposes. Check out the "gallery" on our website for some
  example of how previous customers have used this.

## Registered Users

If you want to know who your users are, you need to work with some user management system that allows users to
register / login / recover their password, etc. The Slots game package doesn't include this facility,
but you can use any of the dozens of PHP user management systems available.

Once that system is installed, you need to follow these steps to integrate the game with it:

1. Modify `slots/includes/config.php` to use the correct table name:
    - Set constant `USERS_TABLE` to the name of the table your system uses to store users.
    - If the ID column in that table is not called `id`, modify constant `USERS_ID_FIELD` to the correct column name.
1. If your users system doesn't have a column in their users table to track user's balance, you must add this column:
    - Run this SQL query against your database: `ALTER TABLE {your_table_name} ADD balance decimal(20,6) DEFAULT 0 NULL;`
    - In that line, change `{your_table_name}` to the name of the users table in your system.
1. If the users system *does* have a column for balance, and it's not called `balance`, then modify constant `USERS_BALANCE_FIELD`
    in file `slots/includes/config.php` to point to the right column.
1. if your visitor reaches the page where the game is, and they are not logged in, they will be redirected
    to your login page. Please set constant `LOGIN_PAGE_URL` to the URL of your login page.

1. Finally, you need to pick the right "login provider" and adapt it to your needs.
   The game includes 2 login providers: `login_freeplay.php` (for FreePlay) and
   `login_custom.php` to integrate with other login systems.

    - Open file `slots/includes/common_includes.php`.
    - At the bottom, you will see several `include` statements, one for each provider included.
    - Comment out the `login_freeplay.php` line, and un-comment the `login_custom.php` one.
    - Now the game is ready to integrate with another system. All we need to do is tell it
      how to find out who's logged in.

    - In file `slots/includes/login_custom.php`, the `LoggedUserID` function at the very top is the one that
        decides who's logged in. By default, it assumes that the current logged user
        can be obtained by looking at a `$_SESSION` variable (in this case, `userID`).
    - You need to edit the name of that `$_SESSION` variable to the one your users system uses. Or, if
        you should be using other mechanism than sessions, then modify this function accordingly to your system's
        requirements.

## Money Management

If you require users to deposit / withdraw real money into their accounts to play Slots, you will also need
 a money management system.  The game package doesn't include this facility,
 but you can use any of the dozens of PHP money management systems available.

The game will simply use whatever database field the money management system uses to keep track of the user's
balance, and modify it as they play / win / lose. Follow the instructions in the "Registered Users" section above to
point the game to the correct balance field.

### "Load more balance" page

If a user tries to start a game, but they don't have enough balance, a dialog will show asking them to "Load more balance".

This dialog will have a button that sends them to the page where they can load more balance on their account. You need
 to configure that button to point to the correct place. Edit `slots/includes/config.php`, set constant `ADD_BALANCE_URL`
 to point to the page in your site where users can load more balance in their account.

Alternatively, if there isn't such a page, modify the dialog text accordingly.



# Setting up Game Types and Prizes

The Slots game is designed so that you can have multiple different designs, odds, behaviours, prizes, etc,
for users to play at different times. This is achieved through the concept of "game types". Each game type defines
all these parameters, and when you display a slot machine, you define which game type it uses.

The Slots package also comes preloaded with several game types that may be what you need already, letting you set up
  easily by simply picking the correct one.

By default, the package will display ALL of these game types, with a title telling you their name, all at once, so
 you can try them and pick which one you like best. Read "Picking a game type" to see how to pick the one you want to
 use.

## Preloaded Game Types

The Slots package comes pre-set with several "types" of slots games that cover the most common requirements for most needs.

These are:

- **default**: A "classic" slot machine where a player has a balance which determines how many times they can spin. 
    When spinning, they may get a prize which translates back into that balance, allowing them to play for longer, 
    or to eventually, if your site provides for it, to withdraw that money.
    This should be your starting point if you are using the slot machine in a "casino" style website, or your site has
    a certain "balance" or "credits" aspect that the user can try to increase by spinning.
    On average, this slot machine pays back about 85%, or in other words, yields a profit of about 15%



## Picking a game type

When first installed, the Slots package will display all these games types in a single page, for you to
 play and experiment with them. However, you will most likely only want to display one of these on your page.

To that end, open file `slots/index.php`, and you'll notice a block of 3 lines that's repeated several times,
one per game type. Simply find the one you want, based on the description of the Game Types above, and remove all the others.

Also remember to delete the `<h2>` line that displays the game type. That's just for your guidance, and shouldn't be
shown to users.

**IMPORTANT!!: You must disable, in your database, the game types you're not using, to prevent cheating**
If you leave a game type enabled in the database, even if you're not displaying it on your site,
cheaters that understand how this game works can access them "behind the scenes", and win prizes
that you didn't intend. Follow the instructions below on section "Enabling / Disabling Game Types"
and disable all the game types you won't use.




## Configuring your games

Even if you choose one of the preloaded game types, you will probably want to do some customization of the behaviour
of these games. All the configuration is in 2 database tables:

- `slots_game_types` which defines the general parameters and behaviour of the game
- `slots_prizes` which defines what prizes each game offers

These tables control different aspects of each game, which we will describe below:

NOTE: All table names are prefixed with `luckscript_` to prevent potential collisions with existing tables.
When the document refers to table `slots_game_types`, you will need to look for
`luckscript_slots_game_types`
in your database.



### Spin Bet / Free to Play / Prizes and Winnings

Users in our system have a "balance" that they use to play games. This balance can be "dollars", "credits", bitcoin, anything.
The system doesn't know about the unit of this balance, just how much the users have, how much games cost, and how much
prizes pay off.

The cost of a game (specified in the `cost` column, or the `min_bet`, `max_bet` columns
of table `slots_game_types`) is how much of this balance
is deducted from a user when they start a game. If you want your users to play for free, you can simply
set these fields to 0.


Be careful if you do this, however, with the setup of your prizes. In general, you allow users to play for free
when they don't really have a balance in your system. In that case, if your prizes aren't "named", the system
will pay them off in "credits" which will mean nothing to them. You should configure your prizes to have a name,
so they'll be meaningful to your users. Read more about this on the "Game Prizes" section.


#### Modifying the user's bet

Some slot machine designs will have buttons to allow the user to increase or decrease their
bet. The bet for a given game type will start by default at `min_bet`, and the user will 
be able to increase it up to `max_bet`, with each button press increasing it by 1.

This will also increase the payout of the prizes shown in the prizes list.

If you set your `min_bet` and `max_bet` to 0, allowing players to play for free, you should
pick a design that doesn't have these buttons, or it will be very confusing to users.

### Enabling / Disabling Game Types

As mentioned above, the game comes preloaded with several game types that are available for users to play.
Even if you don't display some of these on your website by rendering their template, they are still available
to play, and a cheater that understands how the game works internally can take advantage of that.

Game Types can be enabled or disabled, using the `enabled` column of table `slots_game_types`.
Make sure to set this to `0` for all the games that you're not planning to use, to close this door to cheaters.




## Game Prizes

Each game type can be set up to offer either one prize (you either get it or you don't), or multiple different prizes
that the player can get depending on which icons they get when spinning. This, plus the probability of winning,
the payout for each prize, and a few other options are all set on the `slots_prizes` table.

For each prize you want the game to offer, you need a record in the `slots_prizes` table. If there's only one
prize to win, then only one record. If there are 3 possible prizes, then 3 records, and so on.

Each one of these prizes needs to have its fields set in the following way:

- `id`: Leave empty when creating records, MySQL will auto-set this field. **NEVER** change the `id` of an existing record.
- `game_type`: The game type offering this prize. This needs to match the `id` in the `slots_game_types` table.
- `reel1` / `reel2` / `reel3`: These are the icons that the user will get on each reel when he wins this prize.
    - They are either a number (1 to the number of icons you have), or a "matching rule".
    - The number specified the position the reel will fall into. `1` is the first icon, `2` is the second one, etc.
    - Matching rules allow different icons to match for the prize. They are specified as several numbers separated
      by `/`. For example, the `default` slot machine contains a prize that say "3 of any fruit". These are specified
      as `1/3/5`, because the 3 fruits are in positions `1`, `3` and `5` in the reels. When winning one of these prizes,
      the slots will pick randomly between the options for each reel.
- `probability`: The probability that the user will get this prize. It's a number between 0 and 1.0, with 1.0 being 100%
    probability, and 0.5 meaning 50%. Read the next section for more information on how to set the odds to achieve the
    payout / profit percentage you want.
- `payout_credits`: How much balance will be added to the player if they win this prize.
- `payout_winnings`: How many "winning points" will be added to the player if they win this prize. 
    "Prizes vs Winnings" below for the difference between these two fields, and when to use which.

**IMPORTANT**: make sure no two prizes have the same combination of icons. If they do, the one with the
    higher payout one will be used, and the others will be ignored.

If you define a "complex" matching rule for a reel, and yuor chosen design is showing a 
prizes table, please read section "Customizing the Prizes sprites" on how to make the prizes
table render correctly.

### Probabilities and payouts

If the objective of the game is for you to profit from it (as opposed to, say, user
engagement or marketing purposes), then when setting up the prizes for your game,
with their probabilities and payouts, you will be aiming for a certain payout /
profit ratio.

You will need to ensure the configuration you set gives you this payout ratio, or you may end up
losing money on them. This section explains how to calculate that.

Once you've set your prizes for the game, the way to calculate the payout / profit ratio is the following:

- Take all the prizes for the game, and multiply their `payout_credits` by their `probability`.
- Then, sum all of these numbers.
- This total is the payout that your game gives out. In other words, for every credit your players put in, they
    will receive these many credits back on average.
- **You need to make sure that the payout ratio is less than 1.** If the payout ratio is over 1, then you're paying out
    more than you're receiving. This may be what you want, under certain circumstances, but not if you're running the
    game to make a profit.
- Your profit is: `1 - { payout }`. That is, how many credits you keep, out of every credit put in by your players.

For example, let's look at an example configuration. These are its prizes, with their probabilities and payouts:

- Probability: 0.01, Payout: 20
- Probability: 0.05, Payout: 5
- Probability: 0.075, Payout: 2
- Probability: 0.1, Payout: 1
- Probability: 0.1, Payout: 1
- Probability: 0.1, Payout: 1

(in a game with a cost of 1, a payout of 1 is basically allowing the player to play another game for free, no profit
on either side)

The total payout of this game is: `0.01 * 20 + 0.05 * 5 + 0.075 * 2 + 0.1 + 0.1 + 0.1 = 0.90`

In other words, the payout ratio is 90%, and its profit is 10%. **On average**, if 100 games are played,
the player will get back 90 credits and you will have earned 10 credits.

All the preloaded game types that come with the package are set to a payout of 90%.

One of the main changes you might want to make to probabilities, however, is to choose between frequent small prizes,
or infrequent huge prizes. You can make that distinction while keeping the payout constant, but it will radically change
the "feel" of your game, so play around a bit and see what feels right.





#### Stats Analyzer

Once you've configured your prizes and their odds, the Slots include a tool to analyze your
probabilities and verify your configuration gives the payout you are expecting.

**NOTE:** This is done by doing repeated simulated "spins" on your slots, for a given period
of time. This means the payout will not be *exact* as there is randomness involved. This will
validate whether the configuration does what you intended, not provide you an actual number.
That is, if you were aiming for a payout of 90%, and the Stats Analyzer, reports 87%, then your
configuration is probably correct. If it reports 60% or 120%, the configuration is probably wrong.
The longer you let the Analyzer run, the more accurate this reported number will be

To run the Stats Analyzer:

- **Edit the PHP File to configure it:** Open `stats_analyzer.php`
- **Disable the security feature:** The Stats Analyzer could expose your internal prize
    configuration to anyone that knew the internal of the game. To prevent this, by default,
    the Stats Analyzer is disabled. Find the line that starts with `die("Forbidden!` near
    the top of the file and remove. **IMPORTANT:** You must make sure you delete the 
    `stats_analyzer.php` file from your website after you are finished using it, if you will
    run it in your server.
- **Choose the game type to analyze:** By default, the `default` game in your `slots_game_types`
    table will be analyzed. Changed line `$gameType = 'default';` to the game you'd like to test.
- **Choose the running time:** The longer the Stats Analyzer runs, the more accurate the result. Change
    line `$runtime = 25;` to specify how long you'd like to run it for. Note that most web servers
    and browsers will time out after a certain time. If you are running it in the browser and in 
    your server, you may not be able to increase this very much. Try with different numbers until
    you can run without an error, while having the longest runtime possible.
- **Upload the file to your server** 
- **Run the Analyzer**: Navigate to `your_website.com/slots/stats_analyzer.php`
    It will take a long time to load (as long as you configured it to), and will then display all
    the stats. 
    - If you get a timeout error, reduce the runtime.
    - Because there is randomness involved, the results may be skewed. This is more likely to happen
      if you have prizes with very high payouts that are very infrequent, because having one more or
      less of these can have a large impact on the payout. Try running the Analyzer multiple times if
      you can't run it for longer.

If you have a PHP development environment in your own computer, and you have the slots running 
locally, you can set the runtime to a *really* long time (hours, days, potentially) to get very
accurate results. Run `php stats_analyzer.php` from your command line, instead of running it 
through the browser, and the timeouts will not apply. Make sure to set the `$initialBalance` high
enough that a user wouldn't run out of money!


 
#### Prizes vs Winnings

When configuring the payout of prizes, there are two different fields: `payout_credits`
and `payout_winnings`. These two are similar but subtly different.

Credits, in our system are related to a user's balance. And a user's balance determines
how many times they can spin the slots. E.g: A user with a balance of 3 can spin a slot machine 3 times.
  
When a user wins a prize, the `payout_credits` amount for that prize gets added to the user's
balance. That means if they win a prize with `payout_credits = 10`, they can now sping the slots
10 more times.

This may not be what you want. You may have a system where you assign prizes to the users,
either named prizes or "score" in a points system, but you don't let the users spin more times.

This is where `payout_winnings` comes in handy. When a user wins a prize, that prize's 
`payout_winnings` get added to the user's "Day winnings" and "Lifetime Winnings", keeping
a score of how much they've won. But it doesn't increase the user's balance, it doesn't 
let them spin more times.

For example, imagine you have a store where people get 3 spins of the slots for each purchase,
and you have a points system, whereby a user that accumulates a number of points can get some 
benefit. You might set up your slots such that all prizes have `payout_winnings`, but set all
their `payout_credits` to 0. Then when the user makes a purchase you increase their balance by 3,
and you send them to the slots page. The user will only get 3 spins, no matter whether they get 
prizes, and the winnings of those prizes will accumulate as their points.
 
This also applies if you're handing out physical prizes at a booth, for example. You may
set your slots to `min_bet = 0`, and the `payout_credits = 0` for all prizes, and you'd
give these prizes names, such that a dialog with what they won is shown, but the balance 
is unaffected by winning or spinning.

# Look and feel

The look and feel of the Slots game is easily and completely customizable.

## Choosing a theme

The slots come with several design themes. By default, you will see the "Classic" them, but you can
change this by simply picking which theme CSS file to include in the slots page.

Simply change this line:

`<link type="text/css" rel="stylesheet" href="css/template5.css" />`

to point to the CSS file of the theme you would like. You can find the list of all CSS files in the `slots/css`
directory. There is one `slots.css` file, for the styles that apply to every theme, and then one `.css`
file for each theme.

## Customizing images

If you want to change the images to, for example, have your company logo or your product images in there,
simply find the image that you want to replace, in either `slots/img` or `slots/img/theme_name`, and replace
it with a PNG image of the same dimensions.

For the reels that show when spinning, you need to modify `reel_strip.png`, which is a long strip with all icons together.
That's formed of 6 squares of 128x128 pixels. Modify the parts of it that you need, leaving the icons centered within
each square.

The different themes also include an `reel_strip.psd` file to make modifying this easier. If you have Photoshop or any software
that can work with photoshop files, this file has the 6 icons separately, plus guide lines to make it easier to modify.
After modifying that file, simply export it to PNG, replacing the `reel_strip.png`

## Customizing the Prizes sprites

The icons shown in the prizes table are all in image `prizes_sprites.png`.
This is formed of squares of 30x30 pixels. Each square contains an image for each of the 
rules you specify for your prizes.

For each of these squares, there is a corresponding rule in the theme CSS file that positions
the image correctly pointing to the correct square in this sprite. 

Search for `slot_machine_prize_reel_icon` in your CSS file to find these.

When rendering the prizes table, the Slots take the matching rule you specified for each
reel of each prize, and add a CSS class of the form: `prize_{ encoded_matching_rule }`.
For example `prize_6` is the matching rule is `6`, or `prize_1slash3slash5` if the matching
rule is `1/3/5`.

If you are using a matching rule that is not currently included among these CSS selectors,
you will need to add the corresponding icon to the `prizes_sprites.png` file, and also 
will need to add the CSS rule. The easiest way to find the "encoded matching rule" to use
as the CSS class is to look at the rendered HTML for the correct row using your browser's
element inspector and copy it from there. 

The different themes also include an `prizes_sprites.psd` file to make modifying this easier. If you have Photoshop or any software
that can work with photoshop files, this file has the icons separately, plus guide lines to make it easier to modify.
After modifying that file, simply export it to PNG, replacing the `prizes_sprites.png`
 
### Changing the number of possible icons

By default, all themes come with 6 possible icons. You can change this to any number, but all your game types must
provide the same number of icons.

Update the constant `ICONS_PER_REEL` at the top of `slots/includes/prizes_and_reels.php` to reflect the number
of icons you want.

You will also need to change the configuration of the strip height on the client-side:

- Change the line`stripHeight: 720,` in `slots/js/slots.js` to the height in pixels of `reel_strip.png`
- Change the CSS rule `.slot_machine_container .slot_machine_reel_container .slot_machine_reel` in
    the theme CSS file to a height of **3 times** the height of `reel_strip.png`.


### Cached images / CSS files

Depending on your web server configuration, CSS and images may be cached by the browser, in which case your changes
 may not take effect immediately. If that's happening to you, add "cache breakers" to the paths that point to the files
 you modified, by adding `?v=X` (with X being a number you increment) at the end of the path.

For example, if you modify `icons.png` but you don't see the new file take effect:
 - Find all the mentions of `icons.png` in the CSS files, and replace them with `icons.png?v=2`
 - Now you've modified the CSS file, but that may also be cached, so find the mentions of your CSS file, and
    replace them with, for example: `theme.css?v=2`
 - If you modify these again, just increment that number 2 to a higher one, to keep breaking the cache.

To clarify, there is nothing special about `v` here, or the number 2. What you're doing is simply requesting
"a different file", by adding some random stuff to the query string at the end, so the browser thinks it's a new file, and needs
to request it again, but in a way that you're not actually pointing to a different file, because your server
will ignore what you put after the `?` for an image or CSS file.



## Making it look good on mobile / "Responsive Design"

Our games are essentially one large image, so they can't be "responsive" in the usual sense of "changing the
layout of elements to fit the screen width". Instead, what you will want to do is make the game take up the full
width of the screen.

You can do this, if the game is in its own page, with the `meta viewport` HTML tag, by making the page
show, in mobile, zoomed such that the full-width of the game takes the full width of the screen.

The game is, by default, 880px wide, so this is the tag you want to add,
inside the `<head>` section of your HTML document:

`<meta name="viewport" content="width=880px, user-scalable=no, target-densitydpi=device-dpi" />`

This will also work if you're showing the game in an IFrame. In that case, the game will take the full width of the
IFrame, and you'll need to make sure the Iframe takes the full width of the screen on the page where you've added it.

Another option is to change the size of the game to whatever you need, by scaling it using CSS.

Place a `<div>` element around the rendered game, and set this CSS rule on it: `transform: scale(0.5);`.
(`0.5` will make the game half its size. `2` will make it twice its size).



## Customizing other design elements

If you need more customization of the design, you can change anything you need in the `CSS` and image files.

The CSS code is structured such that all the style rules that apply to all themes are in file `slots.css`,
 and all the rules that are specific to each theme are in file `{theme_name}.css`. Mostly these are just the
 paths to the specific images of each theme, and specific positioning coordinates.

If you decide to heavily modify the layout, one recommendation: Try to not change the size of the "window"
through which the reels show. Everythign around that is just images and they are reasonably easy to change,
but if you change the positions of the reels relative to each other, their widths, etc, you will have to 
modify *a lot* of coordinates to get it looking right.


# Spin / Prize logic

When a player spins, the slots internally behave in a way that may seem counter-intuitive.

The slots *first* choose which prize the player will win. 
Then they pick reel positions that would result in that prize.

This may seem backwards, but it makes it much easier to control the odds of a prize coming out.
The alternative is what "old-school" mechanical slot machines do: control the odds of a reel 
position coming out, which indirectly affects the odds of the prize coming out. That is very, very
hard to configure.

Instead, it is much easier to configure the probabilities for a given prize, and then we can try 
random reel positions (if those prizes have complex matching rules) that'll end up in that prize.
It also makes it much easier for you to manually control the outcome of a spin, if you want to do
so (more on this below, under "Code configuration").

## Random spin logic explained

The logic to pick what prize a spin results in, can be thought of like this:

- Create "buckets" from 0 to 1, with a width of the probability you give those prizes.
- Get a random number between 0 to 1
- See which "bucket" that number falls in. 

For example: Let's say you have two prizes, with probabilities of 0.5 and 0.25, respectively.

You will have buckets:

- 0 to 0.5: Prize 1
- 0.5 to 0.75: Prize 2
- 0.75 to 1: Nothing

When you get a random number, if you get anything between 0 and 0.5, the first prize is given.  
If you get anything from 0.5 to 0.75, the second one.  
Anything above 0.75, no prize is given.

So, statistically, 50% of the spins result in Prize 1, 25% in Prize 2, and 25% in no prize.

This is why it's important that your probabilities don't add up to more than 1. If they do, the
player will always win, and the prizes that fall after the 1 will never get picked.

It is ok to have the probabilities add up to exactly 1, however, if you want a slot machine that
always gives out prizes.


## Choosing reels logic explained

Once a prize is chosen, the slots need to pick what positions the reels will fall in.
For most prizes, this is straight forward. If your reels configuration say a prize
is "all cherries", then the slots will just pick cherries.

However, you may have a more complex rule, like, for example, "any fruit" (as seen in the
`default` game type). This is specified as `1/3/5`, for example. In that case, the slots
need to pick "any of 1, 3 or 5" for each of the reels, and it will do so randomly.

More importantly, if you win "no prize", it need to pick random positions for the reels.

Under both of these circumstances, you may have a situation where the slots pick a set of 
reels that give the expected prize **but also a higher prize**. For example, when picking
"any fruit", it may pick "3 cherries", which is actually a higher payout prize. If the 
game were to display 3 cherries but pay out the lower "any fruit" prize, the player would
be understandably confused / upset.

So when picking random reels, the game will afterwards verfiy that they actually result in
the expected prize and not something else. If it gives out something else, it'll try again
until it finds a suitable combination.

**However**, there is a limit, to prevent the game from hanging forever. If it tries 1000
times, and it can't find a suitable combination, it will give up. If you see the error
`Reels not found`, this is what happened.

This doesn't happen under normal circumstances, it only happens if the prize configuration
results in an impossible set of constraints to resolve.

For example, if the "any fruit" prize would pay higher than "3 cherries", then any time the
slots decide to give out the "3 cherries" prize, it'd pick 3 cherries, validate the result, 
find that "any fruit" would actually be the prize given out, and try again.

Or if you have two prizes with "3 cherries" as the winning combination, then whenever one of
the two is picked it'd be fine, and whenever the other one is picked, it'd always resolve to
the first one and error out.

Because of this, it is important that you prize configuration is *consistent*. That means:

- No two prizes have the same prize configuration
- If a certain reel position can give out more than one prize, the "more specific" prize 
  must pay more. That is "3 cherries", "3 bananas" **and** "3 watermelon" **must all** pay
  more than "3 of either cherry, banana or watermelon".


# Code configuration / customization

There are a number of things that can be configured by modifying the game code directly


## Setting initial balance for anonymous users

If you allow anonymous players to play, when a user reaches the game, a record is created for them in the `luckscript_users`
table, and a cookie is set for them. This anonymous user is created with a starting balance of 100, which you can
modify by editing constant `FREEPLAY_CREDITS` in file `slots/includes/login_freeplay.php`.



## Affecting the logic that picks the prize for a game



When starting a spin, the system picks which prize the player will get.


Before it does that using its normal logic, it'll call the function `GetForcedPrize` in
file `slots/includes/custom_hooks.php`. This function allows you to force
a particular prize if you have a particular logic you want to run.

The way this works is: this function can either return `null`, or a number corresponding to a prize ID. If it returns `null`
(the default), then the game goes on to run the regular prize-picking logic. However, if it returns a Prize ID, then that's
the prize the user will get.

The function receives as parameters the game type, and the User ID, so you can make your decisions based on who's playing and
which type of game this is.

As an example, you can use this to make sure the user always wins on their first game, to make them more likely to keep playing:

```
public static function GetForcedPrize($userID, $gameType) {
    $numGames = DB::Scalar("SELECT COUNT(*) FROM luckscript_slots_spins WHERE user_id = '" . DB::DQ($userID) . "';");
    if ($numGames == 0) {
        return 1; // No games exist, so this is their first game. Change the `1` to the ID of a prize that exists in this game type.
    } else {
        return null; // This is not their first game. Let the usual logic pick a prize (or no prize)
    }
}
```

You'll need to modify this code to suit your needs. For example, if you have multiple game types available, then you can't
have a simple hard-coded Prize ID to return, the function will have to pick different ones based on `$gameType`.

In addition to simple things like that, you can have logic as complicated as necessary to do any prize logic you want.
If you want to completely replace the regular game prize picking logic, just set the probability of all prizes to 0, and
then implement your own full logic here. That way, whatever you return will be guaranteed to be the prize picked (even
if you return `null``).



## Custom logic to run when a player wins a prize

When a player wins a prize, the game executes function `PrizeWon` in file `slots/includes/custom_hooks.php`.

This function is there for you to run your custom logic when a player wins. For example, if they win a t-shirt, you may
want to log a record in a database table to create an order in your shipment service. Or you may want to send an e-mail
to the player, or to your internal admin team. If you need *anything* to happen when a player wins, besides them getting
their balance updated based on the prize `payout_credits`, you can do it in this function.

The function receives as parameters:

- `$userID`


- `$gameType`, which allows you to find what slot they were playing in `slots_game_types`
- `$bet`, the amount the user "paid" to spin

- `$prizeID`, the prize the user won.


## Changing the number of possible icons `(ICONS_PER_REEL)`

By default, all the themes use 6 different possible icons that can show when spinning a reel. If you change the number
of icons in `icons.png`, make sure to update the constant `ICONS_PER_REEL` at the top of 
`slots/includes/prizes_and_reels.php` to reflect the number of icons you want.

You will also need to change the configuration of the strip height on the client-side:

- Change the line`stripHeight: 720,` in `slots/js/slots.js` to the height in pixels of `reel_strip.png`
- Change the CSS rule `.slot_machine_container .slot_machine_reel_container .slot_machine_reel` in
    the theme CSS file to a height of **3 times** the height of `reel_strip.png`.



## Allowing reels to land in the blank spaces between icons

You can configure your reels to land in the blank spaces between icons.

If you would like that, update the constant `ALLOW_BLANK_SPACES` at the top of 
`slots/includes/prizes_and_reels.php` to `true`.

Once you do that, reels will automatically fall into the spaces between icons when
the user has won no prize.

You can also configure prizes around these blank spaces. The blank spaces are denoted by
positions in the reel ending in ".5". So, position "2.5" is the blank space between icons
2 and 3. NOTE: There is no 0.5, you should use 6.5 instead (if you're using 6 icons)

You can also specify wildcards "*.0" for "any icon" and "*.5" for "any blank space". You 
may have a prize for "all blank spaces", for example, by setting all 3 reel rules to "*.5".

## Using Reel Odds instead of Prize odds

Instead of setting the probabilities for a prize to come out, you can instead configure
the probabilities for each reel to fall in a specific position. This will indirectly set
the probabilities for the prizes, but it will also give you **full control** of how
often the reels end up in a certain outcome.

You could, for example, set it to it's very common for "cherries" to come out in reels
1 and 2, but very, very unlikely on reel 3. Doing that will give the player the excitement
of "i might win the big prize" without actually giving it away. 

Please note that it's not trivial to figure out the actual payout of the slot machine when
setting reel position odds. We recommend testing your configuration thoroughly with the
Stats Analyzer to make sure the slots are performing the way you would like them to.

The way setting these odds works is having a record in the `slots_reels` table for each
`outcome` (position) in each reel, specifying its `probability`.

The sum of probabilities for one reel will be automatically normalized down to 1, so you 
don’t need to make sure they add up to 1. 

An easy way to look at this is the way that this worked in old, mechanical slot machines. 
Even though one reel would have 6 symbols, in reality the reel had more than 20 images, and 
those 6 symbols were repeated several times. The symbols that were repeated the most came up 
most frequently, obviously. 

Setting slotmachine_reels so that, for example, position 1.0 has probability 1, 
and position 3.0 has probability 7 is the same as having a reel with one banana and 7 watermelons. 
The watermelon will, on average, come up 7 times as often as the banana.

Note that you must set probabilities for all positions in the reel, including the “0.5” 
intervals in between. If you don’t want a position to ever come up, set its probability to 
zero, but the record needs to be there. If you have 6 symbols, you need 12 records: 
1.0, 1.5, 2.0, 2.5, …, 5.0, 5.5, 6.0, 6.5


## Pointing to the PHP file that processes user actions

If you move your files around, and the `slots_action.php` file is not in the same directory as the page that
renders the game, then you will need to point the JS code to the location of the `slots_action.php`.

You do this by modifying the line `actionURL: 'slots_action.php'`, near the top of `slots.js`, to point
to the correct location of the file, relative to the page that renders the game, or relative to the root of
your site if you start the path with `/`.






## Customizing Client Side behaviour

### Animation settings

All the parameters for the animation of the reels are specified at the top of file
`slots.js`.

These allow you to specify how long the reels spin for, how much they move when spinning
(aka, how fast they spin), how much and how long they bounce, etc.

Search for the "config" object at the top of `slots.js` to find these parameters

- alignmentOffset: This is a vertical offset to get the items in the strip aligned perfectly. 
- firstReelStopTime: Time since beginning of spin until the first reel stops spinning and starts bouncing.
- secondReelStopTime: Since first reel's stop time.
- thirdReelStopTime: Since second reel's stop time.
- payoutStopTime: Time since last reel starts bouncing until the counters start increasing and the winning sound plays. Ideally, make it a bit less than bounceTime, so the last reel is still bouncing when the counters light up. 
- reelSpeedDifference: If you want the different reels to move at different speeds, to look "more misaligned" set this value to a non-zero number.
- reelSpeed1Delta, reelSpeed1Time, reelSpeed2Delta:  Reels may have two speeds. They will move at reelSpeed1Delta speed for reelSpeed1Time milliseconds, and then at reelSpeed2Delta for the remaining time. I recommend leaving reelSpeed1Time as 0, and having only one speed, but you can make them go fast for a bit, and then slow down a bit before stopping. 
- positioningTime, bounceTIme, bounceHeight: When the reel stops spinning, it goes into it's final "bounce" until it stops completely. It will move from wherever it is to bounceHeight pixels off of its final position in positioningTime milliseconds, and then it'll bounce around the end line for bounceHeight milliseconds. I recommend leaving these values alone, it's hard to get a better effect than the current one by tweaking them.

**NOTE:** If you change the timings of reel stops, you will need to modify the `spinning.mp3`
sound file because it has its "reel click" sounds hard-coded into specific positions. 


## Updating balance counter on the page

If your page has a balance counter somewhere, for example at the header, then you will want it to be updated when the user
starts a game or wins a prize. There is an empty function called `SlotsBalanceChange`
near the top of `slots.js` for this purpose.

This function receives as a parameter the latest balance of the user according to the server, and here you can write
JS code to update the corresponding elements in your UI.


# Implementing your own backend

There are numerous reasons why you might want to implement your own backend. There are many 
scenarios for these slots where the prize-picking logic is not adequate, or harder to update
than it would be to just send back a simpler response that does the job you need.

If the reason to implement your own backend is that your server does not support PHP, then
the easiest way to approach this is to essentially "translate" the existing PHP code. It should
be pretty easy to read, and all functions document what they are for.

However, if you are implementing your own backend from scratch, all you need to do is reply 
to the client with a JSON object that follows this structure, and the client-side animations
will simply work.

## Example JSON response, explained

```
{
    "success": true,
    "error" : null,
    "reels": [1,1,1],
    "prize": {
        "id": "6",
        "payout_credits": 12,
        "payout_winnings": 12
    },
    "balance": 92,
    "day_winnings": 12,
    "lifetime_winnings": 152
}
```

The JSON file contains the following keys:
- `success`: true or false. If it's false, the animation will be abruptly stopped, and an 
    error will be shown to the user.
- `error`: Only present if `success` is false. The special string `"loggedOut"` will show 
    a message to the user telling them they'be been logged out and they need to log-in again. 
    Any other string will show a "unexpected error" dialog box to the user.
- `reels`:  specifies the position of each reel. It's an array with 3 elements, each of then a 
    number indicating which icon to have in the result line (1-based). If the number ends in 0.5, 
    it represents the blank space between an icon and the next. So, 2 is the 2nd icon in `reel_strip.png`, 
    while 2.5 is the blank space between icons 2 and 3.
- `prize`: Either `null` if no prize was won, or a "Prize Object" described below.

- `balance`: (required) the number of credits available to the user after the spin, including however many credits 
    they may have just won.
- `day_winnings`: (required) The total winnings of the user today. Send 0 if you're not using this feature.
- `lifetime_winnings`: (required) The total winnings of the user in his lifetime. Send 0 if you're not using this feature.
- `last_win`: (optional) string to show in the #lastWin element.

If the user won a prize on this spin, the prize object needs to look like this:
- `id`: (required) the ID of the prize won. Used to highlight the right row in the prizes table. Send a 0 even if you're not using that feature.
- `payout_credits`: (required) how many credits the user just won.Thisistechnically redundant, but we send both this 
    and credits because we don't trust the state of the client. The counter will count up from (credits - payoutCredits) 
    to credits, ignoring how many credits the client "thought it had".
- `payoutWinnings`: (required) If you're using `day_winnings` or `lifetime_winnings`, how much the user won in this spin 
    (equivalent to payoutCredits but for the Winnings elements). Send 0 if you're not using this feature.


# Troubleshooting


- Database connection error messages:
    - `"Warning: mysqli::mysqli(): (HY000/2002): Connection refused"`
    - `"Warning: mysqli::mysqli(): (HY000/2002): Network is unreachable"`
    - `"Warning: mysqli::mysqli(): (HY000/1045): Access denied for user 'xx'@'xx'"`
        - These are caused by having incorrect connection settings to your database. Make sure your database exists, and you
            can connect to it, and configure the constants in `slots/includes/config.php` with the correct credentials.
        - Read more about this at the very top of this document, "Installation".
        - If you don't know your credentials, you should be able to ask your server's hosting provider for them.

- Missing Database Tables / Fields errors:
    - `"Notice: MySQL error Table 'luckscript_xxx' doesn't exist"`
        - This generally happens when you haven't ran the SQL script to create the game's tables. Read more about this
          at the very top of this document, "Installation".
        - It may also happen if you've changed the table name for the users table to your own system's, and misspelled it
            or missed some reference to the original. Read more about this in section "Registered Users"
    - `"Notice: MySQL error Unknown column 'xx' in 'field list'"`
        - This generally happens if you've repointed the game to your own system's users table, and you didn't add the
            fields required by the game, or changed the queries to point to the correct ones.
            Follow the steps in "Registered Users".

- Error: `"Logged User not defined. You must define $userID before requiring slots_template.php"`
    - You must define who the user is before rendering a game, and set their ID in global variable `$userID` before including
        `slots_template.php`. Even if you allow anonymous playing, the system will create a database record to keep track of
        the anonymous players and their games, and you must provide that ID.

- Error: `"Game Type not defined. You must define $gameType before requiring slots_template.php"`
    - You must define what type of game (as defined in the `slots_game_types` database table) to render, in
        global variable `$gameType`

- I get an empty page, with no output.
    - This generally happens when you're getting an error, but PHP's error output is turned off. This is the correct
        setting in production, since exposing error messages to end users may be a security risk, but it will get in the
        way of trying to solve the problem.
        - Try to find Apache's / nginx's error logs and look in there to see if you see any errors.
        - Try to turn on PHP's errors output, and try again to see if you see the error:
            - Add these lines at the top of your PHP file:
            - `ini_set('display_errors', 1);`
            - `ini_set('display_startup_errors', 1);`
            - `error_reporting(E_ALL);`

- I'm getting error "Not logged in"
    - There is an issue with your integration with your users system. Read more in section "Registered Users"

- I'm getting an error message in a nice dialog with a red button that says "Refresh"

    - Other error messages of this type.
    - How to troubleshoot:
        - The server is probably responding to AJAX requests with an error.
        - Open the "Inspector" in your browser (right-click -> Inspect Element in Chrome) and go to the Network tab.
        - Refresh the page. Keep the Network tab open.
        - Do the thing that makes the error come up
        - Look at the requests, there should be an AJAX request to `slots_action.php`. Click on it, and look at the "Response" tab
        - You will probably see a PHP error message there. That should lead you in the right direction.
        - Basically, anything in there that's not a valid JSON string will cause this problem.
        - If the "Response" is blank, see "I get an empty page, with no output" above.

- Error Message "Your browser is not synced with the server. Please refresh and try again."
    - This happens if you open the same game of the same type in multiple windows. There are ways of setting them up, however, that
        could also cause this, particularly if you have several games *of the same game type* in the same page.
    - Read more on section "Out of sync errors"




- Error Message "Reels not found" (you will see this in the Inspector, as explained above)
    - This happens when the game is deciding which reels to show, and it can't find any combination with
        the desired result. The game tries 1000 times, and then gives up with this error message.
    - It's likely that you've set a combination of prizes and reels for those prizes that means
        it's impossible to give out a given prize with any reel positions, without also giving out
        a higher paying prize.
    - Read more about this in sections "Choosing reels logic explained" and "Preventing non-prizes that look like prizes"



- I've changed my images / CSS file / JS file, but the changes are not taking any effect
    - The files you changed are probably cached by the browser.
    - You can empty your cache, but if you've published your site already, some of your users may also have the old
        versions cached.
    - To solve this, you need to change the URLs pointing to these files, in a way that doesn't really affect anything,
        but makes the browser believe it's a different file. See section "Cached images / CSS files" for more information.

- **It still doesn't work:** Email us at [info@slotmachinescript.com](mailto:info@slotmachinescript.com) with a description
    of the problem and we'll help you!


- One of my prizes is not showing icons in the prizes table
    - It is likely you have set a matching rule for your reels that is not contemplated in
      the Prizes Sprites image, or the CSS rules. See section "Customizing the Prizes sprites"
      for more details. 

- Reels are spinning "forever"
    - This means the server is not responding fast enough, or at all.
    - For the best possible user experience, the reels start spinning immediately upon pressing "Spin", 
      instead of waiting for the server response. However, the reels cannot stop spinning until a response 
      is received. Since the clicking sounds of the reels stopping are hard-coded into the background MP3 file, 
      the server must respond before the first reel needs to stop, or the sound will be out of sync. 
      With the default sound, this is about 500ms.
      If the server never responds, the reels will seem to "keep spinning forever". They will actually 
      stop on the 10 second timeout, but users will report "forever".

# FAQ

- Can I change the images to my own logo / my products / etc?
    - Yes, you can change every image by simply replacing the ones provided with your own.
    - Read section "Customizing images" for more information on how / image sizes / etc.
    - Also, check section "Cached images / CSS files" if you don't see the cahnges taking place, or you've already
        published your game to the world.

- Is the game responsive?
    - Yes. You can control exactly how it looks on screen.
    - Read section "Making it look good on mobile / "Responsive Design"" for an explanation on this.

- What jQuery versions does this work with?
    - The game works on jquery 1.7 and above. It's been tested on 1.7.1 and 3.1.



</xmp>

<script src="http://strapdownjs.com/v/0.2/strapdown.js"></script>
</html>

