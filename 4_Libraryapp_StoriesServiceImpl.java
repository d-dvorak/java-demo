package com.marketguards.libraryapp.service;

import com.google.common.collect.Lists;
import com.market_guards.library.model.*;
import com.marketguards.errorhandlerlib.exception.ObjectNotFoundException;
import com.marketguards.libraryapp.domain.*;
import com.marketguards.libraryapp.filter.StoriesFilter;
import com.marketguards.libraryapp.filter.StoriesFilterPredicate;
import com.marketguards.libraryapp.filter.TagStoryFilter;
import com.marketguards.libraryapp.repository.*;
import com.querydsl.core.types.Predicate;
import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stories, a service implemented as part of the library module. (other parts were dictionary and best practices)
 * The users are able to view articles stored in database.
 * This service provided a list of articles (previews), which user could view or filter by keywords or tags.
 * The user was also able to react to an article with reactions similar to Facebook.
 * There was an option to create new article, which stored it into database.
 * Later on, there were "new article indicators" added.
 *
 * Even though the project used JPA, we still used direct sql queries to work with database.
 * written in Java 8
 *
 *
 * @author David Dvořák
 * 2019
 */
@Service
@Transactional
public class StoriesServiceImpl implements StoriesService{

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleReadRepository articleReadRepository;

    @Autowired
    private ReactionTypeRepository reactionTypeRepository;

    @Autowired
    private StoryPlayerReactionRepository storyPlayerReactionRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TagStoryRepository tagStoryRepository;

    @Autowired
    private Mapper mapper;

    // Get list of all possible article, possibly filtered.
    @Override
    public LibraryStoryListDto getStoryPreviews(StoriesFilter storiesFilter) {
        Predicate predicate = StoriesFilterPredicate.by(storiesFilter);
        List<Story> stories = Lists.newArrayList( storyRepository.findAll(predicate, storiesFilter.getPaggingAndSorting()) ); //bad indentation

        // Mapping database object onto dto.
        LibraryStoryListDto libraryStoryListDto = new LibraryStoryListDto();
        stories.forEach(story -> {
            LibraryStoryPreviewDto libraryStoryPreviewDto = mapper.map(story, LibraryStoryPreviewDto.class);
            libraryStoryPreviewDto.setSnippet(story.getArticle().getArticle()
                    .substring(0, Math.min(59,story.getArticle().getArticle().length())) + "...");
            libraryStoryPreviewDto.setReaction(findReactions(storiesFilter.getIdPlayer(),story.getId()));
            libraryStoryPreviewDto.setTags( story.getTagStorySet().stream()
                    .map(tagStory -> mapper.map(tagStory, LibraryTagDto.class))
                    .collect(Collectors.toList()));
            libraryStoryPreviewDto.setSeen(checkSeen(storiesFilter.getIdPlayer(),story.getArticle().getId()));
            libraryStoryListDto.addListItem(libraryStoryPreviewDto);
        });
        if(libraryStoryListDto.getList() != null){
            libraryStoryListDto.setCount(libraryStoryListDto.getList().size());
        }
        libraryStoryListDto.setTotalCount((int) storyRepository.count(predicate));
        return libraryStoryListDto;
    }

    // Get single article detail, set as seen if viewed the first time
    @Override
    public LibraryStoryDto getStory(Long idStory, Long idPlayer) {
        Optional<Story> optionalStory = storyRepository.findById(idStory);
        if( !optionalStory.isPresent() ){
            throw new ObjectNotFoundException("Published story with given id was not found. (" + idStory + ")");
        }
        LibraryStoryDto libraryStoryDto = mapper.map(optionalStory.get(), LibraryStoryDto.class);
        libraryStoryDto.getPreview().setSeen(checkSeen(idPlayer,optionalStory.get().getArticle().getId())); // bad indentation
        libraryStoryDto.getPreview().setTags(findTags(optionalStory.get()));
        libraryStoryDto.getPreview().setReaction(findReactions(idPlayer, idStory));
        libraryStoryDto.getPreview().setId(idStory);
        if(!libraryStoryDto.getPreview().isSeen()){ // bad spacing
            setSeen(idPlayer,optionalStory.get().getArticle().getId());
        }
        return libraryStoryDto;
    }

    // Get list of all tags
    @Override
    public List<LibraryTagDto> getTags(TagStoryFilter tagStoryFilter) {
        List<TagStory> tagStories = Lists.newArrayList( tagStoryRepository.findAllSortedByUsage(
                tagStoryFilter.getPaggingAndSorting().getPageSize(),
                (int) tagStoryFilter.getPaggingAndSorting().getOffset(),
                tagStoryFilter.getSearch()
        ));
        List<LibraryTagDto> libraryTagDtos = new ArrayList<>();
        tagStories.forEach(tagStory -> {
            libraryTagDtos.add(mapper.map(tagStory,LibraryTagDto.class));
        });
        return libraryTagDtos;
    }

    // Update user reaction on an article
    @Override
    public void patchStoryReaction(LibraryStoryReactionDto libraryStoryReactionDto) {
        //find existing reaction (story x player)
        // This could be done completely differently using hibernate and ORM
        Optional<StoryPlayerReaction> optionalReactionStoryPlayer =
                storyPlayerReactionRepository.findByStoryPlayerReactionId_IdPlayerAndStoryPlayerReactionId_IdStory(
                        libraryStoryReactionDto.getIdPlayer(),libraryStoryReactionDto.getIdStory()
                );

        //delete existing (same id reaction type)
        //change existing reaction (different id reaction type)
        //create new reaction (story x player does not exist)
        if( optionalReactionStoryPlayer.isPresent() ){ // bad spacing and indentation
            storyPlayerReactionRepository.delete(optionalReactionStoryPlayer.get());
            if ( libraryStoryReactionDto.getId() != optionalReactionStoryPlayer.get().getStoryPlayerReactionId().getIdReactionType()){
                storyPlayerReactionRepository.save(mapper.map(libraryStoryReactionDto,StoryPlayerReaction.class));
            }
        }else{
            storyPlayerReactionRepository.save(mapper.map(libraryStoryReactionDto,StoryPlayerReaction.class));
        }
    }

    // Save new article including new tagss created by user
    @Override
    public void saveStory(LibraryStoryDto libraryStoryDto, Long idPlayer) {
        //save story
        Story story = mapper.map(libraryStoryDto, Story.class);
        story.getArticle().setAuthorPlayerId(idPlayer);
        articleRepository.save(story.getArticle());
        story = storyRepository.saveAndFlush(story);

        //search if tag exists and save new
        if(libraryStoryDto.getPreview().getTags()!= null) {
            Story finalStory = story;
            libraryStoryDto.getPreview().getTags().forEach(libraryTagDto -> {
                Optional<Tag> optionalTag = tagRepository.findByName(libraryTagDto.getTitle());
                Tag tag;
                if (!optionalTag.isPresent()) {
                    tag = mapper.map(libraryTagDto, Tag.class);
                    tag = tagRepository.saveAndFlush(tag);
                } else {
                    tag = optionalTag.get();
                }
                tagStoryRepository.save(new TagStory(new TagStoryId(tag, finalStory)));
            });
        }
    }

    // Get list of reactions for particular story
    private List<LibraryReactionDto> findReactions(Long idPlayer, Long idStory){
        List<LibraryReactionDto> libraryReactionDtos = new ArrayList<>();

        List<Object []> reactionCounts = storyPlayerReactionRepository.countIdTypeByIdStory(idStory, idPlayer);

        for (Object[] reaction : reactionCounts) {
            LibraryReactionDto libraryReactionDto = new LibraryReactionDto();
            libraryReactionDto.setId(((BigInteger) reaction[0]).longValue());
            libraryReactionDto.setCount(((BigInteger) reaction[1]).intValue());
            libraryReactionDto.setPlayerReaction(((BigInteger) reaction[2]).intValue()==1);
            libraryReactionDtos.add(libraryReactionDto);
        }
        return libraryReactionDtos;
    }

    // Get list of tags for a story
    // This could've been done easier and more elegant via ORM and there wouldn't have to be another db call
    private List<LibraryTagDto> findTags(Story story){
        List<TagStory> tagStories = Lists.newArrayList(tagStoryRepository.findAllByTagStoryId_Story(story));
        List<LibraryTagDto> libraryTagDtos = new ArrayList<>();
        tagStories.forEach(tagStory -> {
            LibraryTagDto libraryTagDto = mapper.map(tagStory.getTagStoryId().getTag(), LibraryTagDto.class);
            libraryTagDtos.add(libraryTagDto);
        });
        return libraryTagDtos;
    }

    //true if article visited
    private boolean checkSeen(Long idPlayer, Long idArticle){
        return (articleReadRepository.findByArticleReadId(new ArticleReadId(idArticle,idPlayer)) != null);
    }

    //new article visit
    private void setSeen(Long idPlayer, Long idArticle){
        articleReadRepository.save(new ArticleRead(new ArticleReadId(idArticle,idPlayer)));
    }

}
